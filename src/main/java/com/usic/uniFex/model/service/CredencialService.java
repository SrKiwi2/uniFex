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
 * <h2>Los dos requisitos</h2>
 * Una credencial sale solo si la venta esta <b>pagada</b> y la persona tiene <b>foto</b>.
 * Pagada significa lo que el sistema ya sabe hoy: se marco "pago al contado" o se subio la
 * imagen del comprobante. No hay un tercer estado de "verificado por administracion" — si
 * algun dia hace falta, es una columna mas y este es el unico sitio que hay que tocar.
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
        return dao.listarDeEdicionActiva().stream().map(this::de).toList();
    }

    /** Las que se pueden imprimir con esa plantilla: es lo que hace la generacion masiva. */
    @Transactional(readOnly = true)
    public List<CredencialDTO> listarAptas(String plantilla) {
        return listar().stream().filter(c -> c.apto(plantilla)).toList();
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
