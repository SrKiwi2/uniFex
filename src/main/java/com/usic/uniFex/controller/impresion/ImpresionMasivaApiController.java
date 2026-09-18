package com.usic.uniFex.controller.impresion;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.dto.PlantillaCredencial;
import com.usic.uniFex.model.service.CredencialPdfService;
import com.usic.uniFex.model.service.CredencialService;
import com.usic.uniFex.model.service.ImpresionMasivaService;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * API para impresión masiva de credenciales EXPOSITOR.
 *
 * <h2>Diferencias con /api/app/credenciales</h2>
 * <ul>
 *   <li>Usa plantilla EXPOSITOR (CREDENCIAL_EXPOSITOR.png): foto izq, QR der, tarjetas blancas.</li>
 *   <li>No exige comprobante ni foto: imprime TODOS los expositores de la edición activa.</li>
 *   <li>No filtra por vendedor (salvo si se pasa vendedorId): imprime TODOS.</li>
 * </ul>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>GET /impresion-masiva          -> lista todas las credenciales de expositores</li>
 *   <li>GET /impresion-masiva/vendedor?{vendedorId} -> filtrado por vendedor</li>
 *   <li>POST /impresion-masiva/pdf    -> genera PDF con plantilla EXPOSITOR (hoja carta, 10x15)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/app/impresion-masiva")
@RequiredArgsConstructor
@PreAuthorize(Roles.ADMINISTRA)
public class ImpresionMasivaApiController {

    private final ImpresionMasivaService impresion;
    private final CredencialPdfService pdfService;
    private final CredencialService credencialService;

    /** Lista todas las credenciales de expositores de la edición activa. */
    @GetMapping
    public List<CredencialDTO> listar(@RequestParam(required = false) Long vendedorId) {
        if (vendedorId != null) {
            return impresion.listarDeVendedor(vendedorId);
        }
        return impresion.listarTodas();
    }

    /** Dos credenciales de 10x15 cm por hoja oficio: frentes arriba, reversos abajo, para cortar y doblar. */
    @PostMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(
            @RequestBody(required = false) PeticionPdf req) {

        List<CredencialDTO> credenciales = (req == null || req.responsables() == null || req.responsables().isEmpty())
                ? impresion.listarTodas()
                : impresion.porResponsables(req.responsables());

        if (credenciales.isEmpty()) {
            return ResponseEntity.status(409)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("No hay credenciales de expositores para imprimir."
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        // Genera codigo QR para cada credencial (usa el mismo servicio que las credenciales normales)
        var conCodigo = credenciales.stream().map(c -> {
            String codigo = credencialService.codigos().codigoDe(c.responsableId());
            return new CredencialDTO(
                    c.responsableId(), codigo, c.nombre(), c.ci(), c.fotoUrl(),
                    c.esTitular(), c.entidad(), c.rubro(), c.categoria(), c.categoriaId(),
                    c.casetas(), c.inscripcionId(), c.conComprobante(), c.conFoto(),
                    c.esExtra(), c.montoExtra(), c.comprobanteExtraUrl(), c.sinCosto());
        }).toList();

        byte[] pdf = pdfService.generarDosPorHoja(
                conCodigo,
                PlantillaCredencial.EXPOSITOR,
                10.0, // ancho 10 cm -> alto 15 cm
                "https://fexpo-uapv2.uap.edu.bo/app", // URL base para QR
                credencialService.codigos()); // CredencialCodigoService para QR

        String nombre = credenciales.size() == 1
                ? "credencial-expositor.pdf"
                : "credenciales-expositor-" + credenciales.size() + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"" + nombre + "\"")
                .body(pdf);
    }

    public record PeticionPdf(@com.fasterxml.jackson.annotation.JsonAlias("ids") List<Long> responsables) {
    }
}
