package com.usic.uniFex.model.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.ICredencialDao;
import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.dto.CredencialFilaView;

import lombok.RequiredArgsConstructor;

/**
 * Quien tiene derecho a credencial y con que datos se imprime.
 *
 * <h2>Los requisitos</h2>
 * Hace falta el <b>comprobante</b> siempre, tambien en las ventas al contado: marcar contado
 * dice como se pago, no que exista el recibo. La <b>foto</b> solo la exige la plantilla que
 * imprime los datos de la persona. Las dos reglas viven en {@link CredencialDTO}.
 *
 * <h2>Quien ve que</h2>
 * Administracion y quien verifica ven todas. Un ADMINISTRATIVO ve solo las de las ventas que
 * el registro, porque la lista completa lleva los datos de los clientes de todos los
 * vendedores. El acotado se hace en la CONSULTA, no filtrando despues en Java: asi los datos
 * ajenos no llegan a salir de la base.
 *
 * <h2>Una credencial por RESPONSABLE, no por venta</h2>
 * Cada persona que atiende la caseta lleva la suya con su foto y su C.I.: es lo que se mira en
 * la puerta. Las casetas y la categoria son las de su entidad, y por eso van juntas en la
 * misma credencial ("14, 15, 16").
 */
@Service
@RequiredArgsConstructor
public class CredencialService {

    private final ICredencialDao dao;
    private final CredencialCodigoService codigos;

    /** Todas las credenciales de la edicion activa, aptas y no aptas. */
    @Transactional(readOnly = true)
    public List<CredencialDTO> listar() {
        return listar(null);
    }

    /**
     * Las credenciales de la edicion activa, acotadas a un vendedor.
     *
     * `soloDeUsuario` nulo = todas, que es lo que ve administracion y quien verifica. Con un
     * id, solo las que salen de las ventas que ESE usuario registro: es lo que ve un
     * ADMINISTRATIVO, que tiene que poder acreditar a sus expositores sin ver los datos de los
     * clientes de sus compañeros.
     */
    @Transactional(readOnly = true)
    public List<CredencialDTO> listar(Long soloDeUsuario) {
        var filas = soloDeUsuario == null
                ? dao.listarDeEdicionActiva()
                : dao.listarDeEdicionActivaDe(soloDeUsuario);
        return filas.stream().map(this::de).toList();
    }

    /** Las que se pueden imprimir con esa plantilla: es lo que hace la generacion masiva. */
    @Transactional(readOnly = true)
    public List<CredencialDTO> listarAptas(String plantilla) {
        return listarAptas(plantilla, null);
    }

    /** Idem, acotado a las ventas de un vendedor. Nulo = todas. */
    @Transactional(readOnly = true)
    public List<CredencialDTO> listarAptas(String plantilla, Long soloDeUsuario) {
        return listar(soloDeUsuario).stream().filter(c -> c.apto(plantilla)).toList();
    }

    /**
     * ¿Esta credencial sale de una venta de ese usuario?
     *
     * La comprobacion va contra la base, no contra la lista que el cliente acaba de recibir:
     * quien pide un PDF manda los ids que quiere.
     */
    @Transactional(readOnly = true)
    public boolean esDeUsuario(Long responsableId, Long usuarioId) {
        return responsableId != null && usuarioId != null
                && dao.esDeUsuario(responsableId, usuarioId);
    }

    @Transactional(readOnly = true)
    public Optional<CredencialDTO> porResponsable(Long responsableId) {
        return dao.buscarPorResponsable(responsableId).map(this::de);
    }

    /**
     * La credencial que corresponde a un codigo de QR.
     *
     * Devuelve vacio si el codigo no esta firmado con el secreto del servidor: un codigo
     * inventado no llega a consultar la base.
     */
    @Transactional(readOnly = true)
    public Optional<CredencialDTO> porCodigo(String codigo) {
        Long id = codigos.responsableDe(codigo);
        return id == null ? Optional.empty() : porResponsable(id);
    }

    /** Las pedidas, en el orden en que se pidieron, saltando las que no existan. */
    @Transactional(readOnly = true)
    public List<CredencialDTO> porResponsables(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream()
                .map(this::porResponsable)
                .flatMap(Optional::stream)
                .toList();
    }

    private CredencialDTO de(CredencialFilaView f) {
        // Ojo: `pago_contado` NO cuenta como comprobante. Antes si contaba, y el resultado era
        // que una venta al contado llegaba a la acreditacion sin ningun respaldo documental.
        boolean conComprobante = f.getComprobante() != null && !f.getComprobante().isBlank();
        String foto = f.getFoto();
        boolean conFoto = foto != null && !foto.isBlank();

        return new CredencialDTO(
                f.getResponsableId(),
                codigos.codigoDe(f.getResponsableId()),
                nombreCompleto(f),
                limpio(f.getCi()),
                conFoto ? "/files/" + foto : null,
                Boolean.TRUE.equals(f.getEsTitular()),
                limpio(f.getEntidad()),
                limpio(f.getRubro()),
                limpio(f.getCategorias()),
                ordenarCasetas(f.getCasetas()),
                f.getInscripcionId(),
                conComprobante,
                conFoto);
    }

    private static String nombreCompleto(CredencialFilaView f) {
        return Stream.of(f.getNombre(), f.getPaterno(), f.getMaterno())
                .map(CredencialService::limpio)
                .filter(s -> s != null && !s.isEmpty())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    /**
     * "14, 9, 100" -> "9, 14, 100".
     *
     * `string_agg` ordena como texto, y ahi "100" va antes que "9". En una credencial que se
     * mira de un vistazo en la puerta, los numeros tienen que salir en el orden en que estan
     * en el plano.
     */
    private static String ordenarCasetas(String crudo) {
        if (crudo == null || crudo.isBlank()) return "";
        return Stream.of(crudo.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .sorted((a, b) -> {
                    try {
                        return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
                    } catch (NumberFormatException e) {
                        return a.compareTo(b);   // codigos no numericos: orden alfabetico
                    }
                })
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private static String limpio(String s) {
        return s == null ? "" : s.trim();
    }
}
