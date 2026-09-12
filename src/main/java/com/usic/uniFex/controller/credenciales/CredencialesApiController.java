package com.usic.uniFex.controller.credenciales;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.service.CredencialCodigoService;
import com.usic.uniFex.model.service.CredencialPdfService;
import com.usic.uniFex.model.service.CredencialService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.IService.ICategoriaService;
import com.usic.uniFex.model.repository.FuncionesInscripcion;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/app/credenciales")
@RequiredArgsConstructor
@Slf4j
public class CredencialesApiController {

    private final FuncionesInscripcion funcionesInscripcion;
    private final ICategoriaService categoriaService;
    private final CredencialService credencialService;
    private final CredencialPdfService pdfService;
    private final CredencialCodigoService codigos;
    private final com.usic.uniFex.model.dao.CredencialImpresionDao impresiones;

    /**
     * Raiz publica que se mete en el QR. Vacia = se deduce de la peticion.
     *
     * Se puede fijar porque detras de un proxy la peticion llega con el host interno, y un QR
     * impreso con "http://localhost:7676" no lo abre nadie. Se imprime una vez y no hay vuelta
     * atras, asi que conviene poder fijarlo en el despliegue.
     */
    @Value("${unifex.publico.base-url:}")
    private String baseUrlPublica;

    /**
     * Categorías disponibles para generar credenciales.
     */
    @GetMapping("/categorias")
    @PreAuthorize(Roles.VERIFICA_CREDENCIALES)
    public List<Map<String, Object>> categorias() {
        return categoriaService.findAll().stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("nombre", c.getNombre());
                    return m;
                })
                .toList();
    }

    /**
     * Inscripciones agrupadas por categoría (edición activa).
     * Devuelve la lista que usa el generador para filtrar por categoría.
     */
    @GetMapping("/inscripciones-por-categoria")
    @PreAuthorize(Roles.VERIFICA_CREDENCIALES)
    public List<Map<String, Object>> inscripcionesPorCategoria() {
        return funcionesInscripcion.fn_inscripciones_por_categoria();
    }

    /**
     * Detalle de una inscripción para generar credenciales.
     * Devuelve TODOS los responsables (uno por credencial).
     */
    @GetMapping("/inscripciones/{id}")
    @PreAuthorize(Roles.VERIFICA_CREDENCIALES)
    public ResponseEntity<Map<String, Object>> detalleInscripcion(@PathVariable Long id) {
        List<Map<String, Object>> lista = funcionesInscripcion.obtener_inscripcion_detalle(id);
        if (lista == null || lista.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // Datos comunes de la venta (tomamos del primer row)
        Map<String, Object> primera = lista.get(0);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("id", primera.get("id"));
        cuerpo.put("fechaCompra", primera.get("fecha_compra"));
        cuerpo.put("nombreEntidad", primera.get("nombre_entidad"));
        cuerpo.put("representanteLegal", primera.get("representante_legal"));
        cuerpo.put("ciRepresentante", primera.get("ci_representante"));
        cuerpo.put("nit", primera.get("nit"));
        cuerpo.put("descripcion", primera.get("descripcion"));
        cuerpo.put("objeto", primera.get("objeto"));
        cuerpo.put("datoqr", primera.get("datoqr"));
        // Responsables: uno por row (cada row = un responsable con su foto, CI, nombre)
        List<Map<String, Object>> responsables = lista.stream().map(row -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("nombrePersona", row.get("nombre_persona"));
            r.put("paternoPersona", row.get("paterno_persona"));
            r.put("maternoPersona", row.get("materno_persona"));
            r.put("ciPersona", row.get("ci_persona"));
            r.put("fotoPersona", row.get("foto_persona"));
            r.put("esTitular", row.get("es_titular") != null ? row.get("es_titular") : false);
            return r;
        }).toList();
        cuerpo.put("responsables", responsables);
        return ResponseEntity.ok(cuerpo);
    }

    /**
     * Todas las credenciales de la edicion activa, cumplan o no los requisitos.
     *
     * Salen TAMBIEN las que no se pueden imprimir, con el motivo: quien prepara las
     * credenciales necesita ver a quien le falta la foto y a quien el comprobante, que es
     * justo el trabajo pendiente. Una lista que solo enseñara las aptas escondería eso.
     */
    @GetMapping
    @PreAuthorize(Roles.VERIFICA_CREDENCIALES)
    public List<Map<String, Object>> listar() {
        // El historial se trae de una vez y se cruza en memoria: una consulta por credencial
        // serian ~800 viajes a la base para pintar una lista.
        Map<Long, Object[]> impresas = new LinkedHashMap<>();
        for (Object[] f : impresiones.resumen()) {
            impresas.put(((Number) f[0]).longValue(), f);
        }

        return credencialService.listar().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("responsableId", c.responsableId());
            m.put("codigo", c.codigo());
            m.put("nombre", c.nombre());
            m.put("ci", c.ci());
            m.put("fotoUrl", c.fotoUrl());
            m.put("esTitular", c.esTitular());
            m.put("entidad", c.entidad());
            m.put("rubro", c.rubro());
            m.put("categoria", c.categoria());
            m.put("casetas", c.casetas());
            m.put("inscripcionId", c.inscripcionId());
            m.put("conComprobante", c.conComprobante());
            m.put("conFoto", c.conFoto());
            // Se resuelve para LAS DOS plantillas en el servidor, en vez de mandar los dos
            // booleanos y que el cliente aplique la regla: asi la regla vive en un solo sitio
            // y cambiar de plantilla en pantalla no obliga a volver a pedir la lista.
            Map<String, Object> listo = new LinkedHashMap<>();
            Map<String, Object> falta = new LinkedHashMap<>();
            for (String plantilla : List.of("CON_ETIQUETAS", "QR_GRANDE")) {
                listo.put(plantilla, c.apto(plantilla));
                falta.put(plantilla, c.faltantes(plantilla));
            }
            m.put("listo", listo);
            m.put("faltantes", falta);

            Object[] imp = impresas.get(c.responsableId());
            m.put("impresa", imp != null);
            m.put("vecesImpresa", imp == null ? 0 : ((Number) imp[1]).intValue());
            m.put("ultimaImpresion", imp == null ? null : String.valueOf(imp[2]));
            m.put("impresaIncompleta", imp != null && Boolean.TRUE.equals(imp[3]));
            return m;
        }).toList();
    }

    /**
     * Lo que se va a imprimir.
     *
     * @param responsables ids concretos; si viene vacio, TODAS las aptas de la edicion activa
     * @param plantilla    "CON_ETIQUETAS" (por defecto) o "QR_GRANDE"
     * @param anchoCm      ancho impreso de la credencial, centrada en una hoja carta
     */
    /**
     * @param forzar imprimir aunque falte algo. Se permite a proposito —a veces el expositor
     *               esta delante y su comprobante llega despues— pero **queda registrado**
     *               quien lo hizo y que faltaba en ese momento.
     */
    public record PeticionPdf(List<Long> responsables, String plantilla, Double anchoCm,
                              Boolean forzar) {
    }

    @PostMapping("/pdf")
    @PreAuthorize(Roles.VERIFICA_CREDENCIALES)
    public ResponseEntity<byte[]> pdf(@RequestBody(required = false) PeticionPdf req) {
        String plantilla = (req == null || req.plantilla() == null || req.plantilla().isBlank())
                ? "CON_ETIQUETAS" : req.plantilla().trim().toUpperCase();
        boolean forzar = req != null && Boolean.TRUE.equals(req.forzar());

        List<Long> ids = req == null ? null : req.responsables();
        List<CredencialDTO> elegidas = (ids == null || ids.isEmpty())
                ? credencialService.listarAptas(plantilla)
                : credencialService.porResponsables(ids);

        // Sin `forzar`, no se imprime lo incompleto: descubrir despues de imprimir 300 que
        // media tanda no sirve es caro. Con `forzar`, se imprime igual y queda constancia.
        List<CredencialDTO> imprimibles = forzar
                ? elegidas
                : elegidas.stream().filter(c -> c.apto(plantilla)).toList();
        if (imprimibles.isEmpty()) {
            return ResponseEntity.status(409)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Ninguna de las credenciales pedidas cumple los requisitos para la "
                            + "plantilla elegida. Falta el comprobante"
                            + (CredencialDTO.requiereFoto(plantilla) ? ", o la foto del responsable." : "."))
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        byte[] pdf = pdfService.generar(
                imprimibles,
                CredencialPdfService.porNombre(plantilla),
                req == null || req.anchoCm() == null
                        ? CredencialPdfService.ANCHO_CM_POR_DEFECTO : req.anchoCm(),
                raizPublica(),
                codigos);

        // Se registra DESPUES de generar: si el PDF falla, no queda una impresion que nunca
        // llego al papel.
        Long quien = usuarioActual();
        for (CredencialDTO c : imprimibles) {
            impresiones.registrar(c.responsableId(), c.inscripcionId(), plantilla,
                    String.join(", ", c.faltantes(plantilla)), quien);
        }

        String nombre = imprimibles.size() == 1
                ? "credencial-" + imprimibles.get(0).codigo() + ".pdf"
                : "credenciales-" + imprimibles.size() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"" + nombre + "\"")
                .body(pdf);
    }

    /** Quien imprimio esta credencial, cuando, con que plantilla y que faltaba entonces. */
    @GetMapping("/{responsableId}/impresiones")
    @PreAuthorize(Roles.VERIFICA_CREDENCIALES)
    public List<Map<String, Object>> impresiones(@PathVariable Long responsableId) {
        return impresiones.historial(responsableId).stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cuando", String.valueOf(f[0]));
            m.put("plantilla", f[1]);
            m.put("faltaba", f[2]);
            m.put("usuario", f[3]);
            return m;
        }).toList();
    }

    /** La raiz con la que se arma la URL del QR. */
    private String raizPublica() {
        if (baseUrlPublica != null && !baseUrlPublica.isBlank()) return baseUrlPublica.trim();
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }

    private boolean esAdministracion() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return false;
        return a.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Roles.AUTORIDADES_ADMINISTRA::contains);
    }
}