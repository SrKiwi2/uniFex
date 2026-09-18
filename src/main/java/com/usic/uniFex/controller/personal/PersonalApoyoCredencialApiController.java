package com.usic.uniFex.controller.personal;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.usic.uniFex.model.IService.IPersonalApoyoService;
import com.usic.uniFex.model.dto.ApoyoCredencialDTO;
import com.usic.uniFex.model.entity.PersonalApoyo;
import com.usic.uniFex.model.service.ApoyoCodigoService;
import com.usic.uniFex.model.service.ApoyoCredencialPdfService;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Credenciales del personal de apoyo: vista solo de administracion.
 *
 * Lista TODAS las fichas activas agrupadas por dependencia en el cliente e imprime sus
 * credenciales 10x15 con QR (FXA-...). No hay "aptas/no aptas" como en expositores: el
 * apoyo no exige comprobante ni foto, asi que todo lo activo se puede imprimir.
 */
@RestController
@RequestMapping("/api/app/personal-apoyo/credenciales")
@RequiredArgsConstructor
@PreAuthorize(Roles.ADMINISTRA)
public class PersonalApoyoCredencialApiController {

    private final IPersonalApoyoService apoyo;
    private final ApoyoCodigoService codigos;
    private final ApoyoCredencialPdfService pdf;

    @Value("${unifex.publico.base-url:}")
    private String baseUrlPublica;

    public record PeticionPdf(List<Long> ids) {
    }

    @GetMapping
    public List<ApoyoCredencialDTO> listar() {
        return apoyo.listarPersonalApoyo().stream()
                .map(p -> ApoyoCredencialDTO.de(p, codigos))
                .toList();
    }

    @PostMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@RequestBody(required = false) PeticionPdf req) {
        List<Long> ids = req == null ? null : req.ids();
        List<PersonalApoyo> fichas = (ids == null || ids.isEmpty())
                ? apoyo.listarPersonalApoyo()
                : ids.stream().map(apoyo::findById).toList();
        List<ApoyoCredencialDTO> lista = fichas.stream()
                .filter(p -> p != null && !"X".equalsIgnoreCase(p.getEstado()))
                .map(p -> ApoyoCredencialDTO.de(p, codigos))
                .toList();
        if (lista.isEmpty()) {
            return ResponseEntity.status(409)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("No hay personal de apoyo activo para imprimir."
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        byte[] bytes = pdf.generar(lista, raizPublica(), codigos);
        String nombre = lista.size() == 1
                ? "credencial-apoyo-" + lista.get(0).codigo() + ".pdf"
                : "credenciales-apoyo-" + lista.size() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"" + nombre + "\"")
                .body(bytes);
    }

    /** La raiz con la que se arma la URL del QR (misma regla que en expositores). */
    private String raizPublica() {
        if (baseUrlPublica != null && !baseUrlPublica.isBlank()) return baseUrlPublica.trim();
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    @PostMapping("/pdf/duplex")
    public ResponseEntity<byte[]> pdfDuplex(@RequestBody(required = false) PeticionPdf req) {
        List<Long> ids = req == null ? null : req.ids();
        List<PersonalApoyo> fichas = (ids == null || ids.isEmpty())
                ? apoyo.listarPersonalApoyo()
                : ids.stream().map(apoyo::findById).toList();
        List<ApoyoCredencialDTO> lista = fichas.stream()
                .filter(p -> p != null && !"X".equalsIgnoreCase(p.getEstado()))
                .map(p -> ApoyoCredencialDTO.de(p, codigos))
                .toList();
        if (lista.isEmpty()) {
            return ResponseEntity.status(409)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("No hay personal de apoyo activo para imprimir."
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        byte[] bytes = pdf.generarDuplex4(lista, raizPublica(), codigos);
        String nombre = "credenciales-apoyo-duplex-" + lista.size() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"" + nombre + "\"")
                .body(bytes);
    }
}
