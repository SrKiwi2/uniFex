package com.usic.uniFex.model.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.ICredencialDao;
import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.dto.CredencialFilaView;
import com.usic.uniFex.model.dto.PlantillaCredencial;

import lombok.RequiredArgsConstructor;

/**
 * Impresión masiva de credenciales EXPOSITOR (plantilla EXPOSITOR).
 *
 * <h2>Diferencias con la acreditación normal</h2>
 * <ul>
 *   <li>Usa la plantilla EXPOSITOR (CREDENCIAL_EXPOSITOR.png) que tiene foto a la izq,
 *       QR a la der y tarjetas blancas para datos.</li>
 *   <li>No exige comprobante ni foto: son credenciales de expositores que ya
 *       compraron su stand, no necesitan validar pago.</li>
 *   <li>Toma TODOS los responsables de la edicion activa (o de un vendedor si se filtra).</li>
 * </ul>
 *
 * <h2>Lo que imprime</h2>
 * Por cada responsable de cada entidad con caseta vendida, imprime una credencial
 * con la plantilla EXPOSITOR: foto en circulo izq, QR en recuadro azul der,
 * y tarjetas blancas con nombre, entidad, C.I., caseta, zona.
 *
 * <h2>Acceso</h2>
 * Solo administrador (Roles.ADMINISTRA). No tiene recorte por vendedor: imprime
 * todas las credenciales de expositores de la edicion activa.
 */
@Service
@RequiredArgsConstructor
public class ImpresionMasivaService {

    private final ICredencialDao dao;

    /**
     * Todas las credenciales de expositores de la edicion activa.
     *
     * No filtra por aptitud (comprobante/foto): las de expositores se imprimen
     * aunque falte foto o comprobante, porque ya compraron su stand.
     * Solo excluye las que esten anuladas o sin casetas.
     */
    @Transactional(readOnly = true)
    public List<CredencialDTO> listarTodas() {
        return dao.listarDeEdicionActiva().stream()
                .map(this::de)
                .toList();
    }

    /**
     * Credenciales de un vendedor concreto (ADMINISTRATIVO).
     * Para que un vendedor pueda imprimir solo las credenciales de sus ventas.
     */
    @Transactional(readOnly = true)
    public List<CredencialDTO> listarDeVendedor(Long vendedorId) {
        return dao.listarDeEdicionActivaDe(vendedorId).stream()
                .map(this::de)
                .toList();
    }

    /**
     * Credenciales por ids concretos (para el botón "Imprimir marcadas").
     */
    @Transactional(readOnly = true)
    public List<CredencialDTO> porResponsables(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream()
                .map(dao::buscarPorResponsable)
                .flatMap(Optional::stream)
                .map(this::de)
                .toList();
    }

    /** Todas las credenciales de una inscripcion concreta. */
    @Transactional(readOnly = true)
    public List<CredencialDTO> porInscripcion(Long inscripcionId) {
        if (inscripcionId == null) return List.of();
        return listarTodas().stream()
                .filter(c -> inscripcionId.equals(c.inscripcionId()))
                .toList();
    }

    /**
     * Convierte una fila SQL a DTO para la plantilla EXPOSITOR.
     *
     * <h2>Reglas para EXPOSITOR</h2>
     * <ul>
     *   <li><b>Siempre apta</b>: no exige comprobante ni foto.</li>
     *   <li><b>Foto</b>: si existe, se pone en el círculo azul; si no, se deja el avatar genérico.</li>
     *   <li><b>Datos</b>: nombre, entidad+rubro, C.I., casetas, zona.</li>
     * </ul>
     */
    private CredencialDTO de(CredencialFilaView f) {
        String foto = f.getFoto();
        boolean conFoto = foto != null && !foto.isBlank();

        return new CredencialDTO(
                f.getResponsableId(),
                null, // codigo QR no se usa en expositor (se genera en el PDF)
                nombreCompleto(f),
                limpio(f.getCi()),
                foto != null && !foto.isBlank() ? "/files/" + foto : null,
                Boolean.TRUE.equals(f.getEsTitular()),
                limpio(f.getEntidad()),
                limpio(f.getRubro()),
                limpio(f.getCategorias()),
                f.getCategoriaId(),
                ordenarCasetas(f.getCasetas()),
                f.getInscripcionId(),
                true,  // conComprobante = true (siempre apta para expositor)
                conFoto,
                Boolean.TRUE.equals(f.getEsExtra()),
                f.getMontoExtra(),
                (f.getComprobanteExtra() != null && !f.getComprobanteExtra().isBlank())
                        ? "/files/" + f.getComprobanteExtra() : null,
                false); // sinCosto = false (no aplica para expositor)
    }

    private static String nombreCompleto(CredencialFilaView f) {
        return Stream.of(f.getNombre(), f.getPaterno(), f.getMaterno())
                .map(CredencialService::limpio)
                .filter(s -> s != null && !s.isEmpty())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    private static String ordenarCasetas(String crudo) {
        if (crudo == null || crudo.isBlank()) return "";
        return Stream.of(crudo.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .sorted((a, b) -> {
                    try {
                        return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
                    } catch (NumberFormatException e) {
                        return a.compareTo(b);
                    }
                })
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private static String limpio(String s) {
        return s == null ? "" : s.trim();
    }
}