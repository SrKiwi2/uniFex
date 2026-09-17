package com.usic.uniFex.controller.credenciales;

import java.io.ByteArrayOutputStream;
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
import com.usic.uniFex.model.dto.PlantillaCredencial;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.service.CredencialCodigoService;
import com.usic.uniFex.model.service.CredencialPdfService;
import com.usic.uniFex.model.service.CredencialImagenService;
import com.usic.uniFex.model.service.CredencialService;
import com.usic.uniFex.model.service.ReciboPdfService;
import com.usic.uniFex.model.service.WhatsAppService;
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
    private final CredencialImagenService imagenService;
    private final CredencialCodigoService codigos;
    private final ReciboPdfService reciboPdfService;
    private final WhatsAppService whatsApp;
    private final IInscripcionService inscripcionService;
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
     * Las plantillas que se pueden elegir para imprimir.
     *
     * Viaja desde el servidor y no escrita a mano en la pantalla porque la lista tiene que ser
     * LA MISMA que la que usa el generador: cuando estaban en dos sitios, una plantilla añadida
     * solo en la pantalla salia en los botones pero llegaba al servidor sin existir, y una
     * añadida solo en el servidor no habia forma de elegirla.
     *
     * `proporcion` (alto/ancho) es para la vista previa sobre la hoja carta, y sale de medir la
     * imagen de verdad.
     */
    @GetMapping("/plantillas")
    @PreAuthorize(Roles.USA_CREDENCIALES)
    public List<Map<String, Object>> plantillas() {
        return PlantillaCredencial.CATALOGO.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id());
            m.put("etiqueta", p.etiqueta());
            m.put("detalle", p.detalle());
            m.put("requiereFoto", p.requiereFoto());
            m.put("proporcion", pdfService.proporcion(p));
            return m;
        }).toList();
    }

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
    @PreAuthorize(Roles.USA_CREDENCIALES)
    public List<Map<String, Object>> listar() {
        Long soloMias = alcanceDelUsuario();
        // El historial se trae de una vez y se cruza en memoria: una consulta por credencial
        // serian ~800 viajes a la base para pintar una lista.
        Map<Long, Object[]> impresas = new LinkedHashMap<>();
        for (Object[] f : impresiones.resumen()) {
            impresas.put(((Number) f[0]).longValue(), f);
        }

        return credencialService.listar(soloMias).stream().map(c -> {
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
            m.put("categoriaId", c.categoriaId());
            m.put("casetas", c.casetas());
            m.put("inscripcionId", c.inscripcionId());
            m.put("conComprobante", c.conComprobante());
            m.put("conFoto", c.conFoto());
            // El distintivo del que se agrego POR ENCIMA del derecho y pago (V34). Sin el, una
            // credencial de pago se mezcla con las de derecho y no hay forma de saber cual hubo
            // que cobrar. El detalle del cobro vive en la pantalla "Responsables extra".
            //
            // OJO: esta lista NO es el DTO, es un mapa armado a mano. Añadir el campo solo a
            // `CredencialDTO` no cambia nada de lo que ve la pantalla — pasó, y costó encontrarlo.
            m.put("esExtra", c.esExtra());
            m.put("montoExtra", c.montoExtra());
            m.put("comprobanteExtraUrl", c.comprobanteExtraUrl());
            // Para que la pantalla EXPLIQUE por que a esa fila no se le pide comprobante. Sin
            // esto, una credencial sin recibo y marcada "Lista" al lado de otra bloqueada por
            // lo mismo parece un fallo del sistema, y la diferencia —que una no cuesta nada—
            // no se puede adivinar desde la lista.
            m.put("sinCosto", c.sinCosto());
            m.put("requiereComprobante", c.requiereComprobante());
            // Se resuelve para LAS DOS plantillas en el servidor, en vez de mandar los dos
            // booleanos y que el cliente aplique la regla: asi la regla vive en un solo sitio
            // y cambiar de plantilla en pantalla no obliga a volver a pedir la lista.
            Map<String, Object> listo = new LinkedHashMap<>();
            Map<String, Object> falta = new LinkedHashMap<>();
            for (PlantillaCredencial p : PlantillaCredencial.CATALOGO) {
                listo.put(p.id(), c.apto(p.id()));
                falta.put(p.id(), c.faltantes(p.id()));
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

    public record PeticionWhatsApp(Long inscripcionId, List<Long> responsables, Boolean incluirRecibo) {
    }

    @PostMapping("/pdf")
    @PreAuthorize(Roles.USA_CREDENCIALES)
    public ResponseEntity<byte[]> pdf(@RequestBody(required = false) PeticionPdf req) {
        String pedida = req == null ? null : req.plantilla();
        // Una plantilla que no existe se rechaza en vez de caer en la de por defecto. Cuando
        // caia, el PDF salia 200 y con la plantilla equivocada: eso solo se descubre mirando el
        // papel, y para entonces ya se imprimieron trescientas.
        PlantillaCredencial disposicion;
        if (pedida == null || pedida.isBlank()) {
            disposicion = PlantillaCredencial.POR_DEFECTO;
        } else {
            var hallada = PlantillaCredencial.de(pedida);
            if (hallada.isEmpty()) {
                log.warn("Se pidio imprimir con la plantilla desconocida '{}'", pedida);
                return ResponseEntity.badRequest()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(("No existe la plantilla '" + pedida.trim() + "'.")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            disposicion = hallada.get();
        }
        String plantilla = disposicion.id();
        boolean forzar = req != null && Boolean.TRUE.equals(req.forzar());
        Long soloMias = alcanceDelUsuario();

        List<Long> ids = req == null ? null : req.responsables();
        // Un vendedor que pide "todas" recibe todas LAS SUYAS. Sin acotar aqui, el boton de
        // generacion masiva le imprimiria la feria entera.
        List<CredencialDTO> elegidas = (ids == null || ids.isEmpty())
                ? credencialService.listarAptas(plantilla, soloMias)
                : credencialService.porResponsables(ids);

        // Y si manda ids concretos, se comprueban contra la base uno por uno. La lista que el
        // cliente tiene no sirve de control: los ids los escribe quien llama.
        if (soloMias != null) {
            for (CredencialDTO c : elegidas) {
                if (!credencialService.esDeUsuario(c.responsableId(), soloMias)) {
                    log.warn("El usuario {} pidio imprimir la credencial {}, que no es de una venta suya",
                            soloMias, c.responsableId());
                    return ResponseEntity.status(403)
                            .contentType(MediaType.TEXT_PLAIN)
                            .body(("Solo puedes generar las credenciales de las ventas que "
                                    + "registraste.").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }

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
                disposicion,
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

    /**
     * La credencial virtual de un responsable, como IMAGEN para el telefono.
     *
     * Va aparte del PDF a proposito. Esta credencial no se imprime: se manda al telefono del
     * expositor y se enseña en la puerta desde la pantalla, asi que una hoja carta con la
     * credencial centrada seria justo lo que no sirve. Es un PNG vertical, del tamaño de la
     * plantilla.
     *
     * Se registra como impresion igual que el PDF: lo que importa del registro es que se
     * ENTREGO una credencial, no en que soporte.
     */
    @GetMapping("/{responsableId}/virtual")
    @PreAuthorize(Roles.USA_CREDENCIALES)
    public ResponseEntity<byte[]> virtual(@PathVariable Long responsableId,
                                           @RequestParam(value = "forzar", defaultValue = "false") boolean forzar) {
        Long soloMias = alcanceDelUsuario();
        if (soloMias != null && !credencialService.esDeUsuario(responsableId, soloMias)) {
            return ResponseEntity.status(403).build();
        }
        CredencialDTO c = credencialService.porResponsable(responsableId).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();

        PlantillaCredencial p = PlantillaCredencial.CREDENCIAL_VIRTUAL;
        if (!forzar && !c.apto(p.id())) {
            return ResponseEntity.status(409)
                    .contentType(MediaType.TEXT_PLAIN)
                    // Los motivos ya vienen redactados ("sin comprobante", "sin foto"):
                    // anteponerles "falta" daba "falta sin comprobante", que no se lee.
                    .body(("Todavía no se puede emitir esta credencial: "
                            + String.join(" y ", c.faltantes(p.id())) + ".")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        byte[] png = imagenService.generar(c, p, raizPublica());
        impresiones.registrar(c.responsableId(), c.inscripcionId(), p.id(),
                String.join(", ", c.faltantes(p.id())), usuarioActual());

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Content-Disposition",
                        "inline; filename=\"credencial-" + c.codigo() + ".png\"")
                .body(png);
    }

    /**
     * Reenvia credenciales con sus recibos de cupo extra. El recibo de venta es opcional.
     *
     * Si `responsables` viene vacio, se envian todas las credenciales listas de la inscripcion.
     * Si trae ids, se envia solo esa seleccion. Un vendedor queda limitado a sus propias ventas.
     */
    @PostMapping("/whatsapp")
    @PreAuthorize(Roles.USA_CREDENCIALES)
    public ResponseEntity<Map<String, Object>> whatsapp(@RequestBody(required = false) PeticionWhatsApp req) {
        log.info("[WHATSAPP-REENVIO] Peticion recibida inscripcion={} responsables={}",
                req == null ? null : req.inscripcionId(), req == null ? null : req.responsables());
        if (req == null || req.inscripcionId() == null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "Falta la inscripción"));
        }
        if (!whatsApp.habilitado()) {
            return ResponseEntity.status(409).body(Map.of("ok", false, "mensaje", "WhatsApp no está configurado"));
        }

        Inscripcion inscripcion = inscripcionService.findById(req.inscripcionId());
        if (inscripcion == null || inscripcion.getEntidad() == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "La inscripción no existe"));
        }
        Long soloMias = alcanceDelUsuario();
        if (soloMias != null && !soloMias.equals(inscripcion.getRegistroIdUsuario())) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "mensaje", "Solo puedes reenviar tus ventas"));
        }

        String celular = normalizarCelular(inscripcion.getEntidad().getCelularRepresentante());
        log.info("[WHATSAPP-REENVIO] Inscripcion={} entidad='{}' celularOriginal='{}' celularNormalizado='{}'",
                req.inscripcionId(), inscripcion.getEntidad().getNombre(),
                inscripcion.getEntidad().getCelularRepresentante(), celular);
        if (celular == null || celular.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "La venta no tiene celular del cliente"));
        }

        PlantillaCredencial plantilla = PlantillaCredencial.CREDENCIAL_VIRTUAL;
        List<Long> seleccion = req.responsables() == null ? List.of() : req.responsables();
        var todas = credencialService.porInscripcion(req.inscripcionId());
        log.info("[WHATSAPP-REENVIO] Credenciales de inscripcion={} total={} seleccion={}",
                req.inscripcionId(), todas.size(), seleccion);
        List<CredencialDTO> credenciales = todas.stream()
                .filter(c -> seleccion.isEmpty() || seleccion.contains(c.responsableId()))
                .peek(c -> log.info("[WHATSAPP-REENVIO] Candidata responsable={} nombre='{}' fotoUrl='{}' conFoto={} conComprobante={} aptaVirtual={}",
                        c.responsableId(), c.nombre(), c.fotoUrl(), c.conFoto(), c.conComprobante(), c.apto(plantilla.id())))
                .filter(c -> c.apto(plantilla.id()))
                .toList();
        log.info("[WHATSAPP-REENVIO] Credenciales aptas inscripcion={} cantidad={}",
                req.inscripcionId(), credenciales.size());
        if (credenciales.isEmpty()) {
            return ResponseEntity.status(409).body(Map.of("ok", false,
                    "mensaje", "No hay credenciales listas para reenviar. Falta comprobante o foto."));
        }

        try {
            byte[] recibo = null;
            if (Boolean.TRUE.equals(req.incluirRecibo())) {
                ByteArrayOutputStream salida = new ByteArrayOutputStream();
                reciboPdfService.generarRecibo(req.inscripcionId(), salida);
                recibo = salida.toByteArray();
                log.info("[WHATSAPP-REENVIO] Recibo generado inscripcion={} bytes={}",
                        req.inscripcionId(), recibo.length);
            }
            List<WhatsAppService.ReciboExtra> recibosExtra = new java.util.ArrayList<>();
            for (CredencialDTO credencial : credenciales) {
                if (!credencial.esExtra()) continue;
                ByteArrayOutputStream salida = new ByteArrayOutputStream();
                reciboPdfService.generarReciboResponsableExtra(credencial.responsableId(), salida);
                recibosExtra.add(new WhatsAppService.ReciboExtra(credencial.responsableId(),
                        credencial.nombre(), salida.toByteArray()));
            }
            List<byte[]> imagenes = credenciales.stream()
                    .map(c -> imagenService.generar(c, plantilla, raizPublica()))
                    .toList();
            log.info("[WHATSAPP-REENVIO] Imagenes de credencial generadas inscripcion={} cantidad={}",
                    req.inscripcionId(), imagenes.size());
            whatsApp.enviarBienvenidaVentaConPdfs(celular, inscripcion.getEntidad().getNombre(),
                    req.inscripcionId(), recibo, imagenes, raizPublica(), recibosExtra);

            return ResponseEntity.ok(Map.of("ok", true,
                    "mensaje", "Reenvío enviado por WhatsApp",
                    "credenciales", credenciales.size()));
        } catch (Exception e) {
            log.warn("No se pudo reenviar WhatsApp de inscripcion {}: {}", req.inscripcionId(), e.getMessage());
            return ResponseEntity.status(500).body(Map.of("ok", false,
                    "mensaje", "No se pudo reenviar por WhatsApp"));
        }
    }

    /** Quien imprimio esta credencial, cuando, con que plantilla y que faltaba entonces. */
    @GetMapping("/{responsableId}/impresiones")
    @PreAuthorize(Roles.USA_CREDENCIALES)
    public List<Map<String, Object>> impresiones(@PathVariable Long responsableId) {
        Long soloMias = alcanceDelUsuario();
        if (soloMias != null && !credencialService.esDeUsuario(responsableId, soloMias)) {
            return List.of();
        }
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

    private static String normalizarCelular(String celular) {
        if (celular == null || celular.isBlank()) return null;
        String n = celular.replaceAll("[^0-9]", "");
        if (n.startsWith("0")) n = n.substring(1);
        return n.length() == 8 ? "591" + n : n;
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }

    /**
     * Hasta donde llega este usuario: {@code null} = toda la feria, o su id = solo sus ventas.
     *
     * Administracion y VERIFICADOR preparan la acreditacion de todos. Un ADMINISTRATIVO
     * acredita a los expositores que el vendio, y nada mas: la lista completa lleva nombres,
     * C.I. y telefonos de los clientes de sus compañeros.
     */
    private Long alcanceDelUsuario() {
        return (esAdministracion() || esVerificador()) ? null : usuarioActual();
    }

    private boolean esVerificador() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_VERIFICADOR"::equals);
    }

    private boolean esAdministracion() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return false;
        return a.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Roles.AUTORIDADES_ADMINISTRA::contains);
    }
}
