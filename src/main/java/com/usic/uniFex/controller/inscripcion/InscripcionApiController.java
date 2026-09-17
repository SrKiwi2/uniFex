package com.usic.uniFex.controller.inscripcion;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.usic.uniFex.model.service.EdicionVentaService;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.dao.IResponsableDao;
import com.usic.uniFex.model.dto.InscripcionDetalleDTO;
import com.usic.uniFex.model.dto.InscripcionListadoDTO;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.Responsable;
import com.usic.uniFex.model.service.AuditoriaService;
import com.usic.uniFex.model.service.CancelarInscripcionService;
import com.usic.uniFex.model.service.ReciboPdfService;
import com.usic.uniFex.model.service.ResponsableFotoService;
import com.usic.uniFex.model.service.RegistroVentaService;
import com.usic.uniFex.model.service.ResponsableExtraService;
import com.usic.uniFex.model.service.VendedorAsignacionService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Inscripciones (ventas) desde la SPA.
 *
 * **La autorizacion va por metodo, no por clase.** Antes toda la clase estaba anotada con
 * {@code ADMINISTRA}, lo cual servia mientras solo habia un listado de administracion; pero
 * registrar una venta es precisamente lo que hace el vendedor, asi que una anotacion de clase
 * habria dejado fuera a los 35 usuarios que mas la necesitan.
 */
@RestController
@RequestMapping("/api/app/inscripciones")
@RequiredArgsConstructor
@Slf4j
public class InscripcionApiController {

    private final IInscripcionService inscripcionService;
    private final IResponsableDao responsableDao;

    private final com.usic.uniFex.model.service.ResponsableExtraService responsableExtra;
    private final RegistroVentaService registroVenta;
    private final EdicionVentaService edicionVenta;
    private final ReciboPdfService reciboPdfService;
    private final CancelarInscripcionService cancelarInscripcion;
    private final ResponsableFotoService responsableFoto;
    private final VendedorAsignacionService vendedorAsignacion;

    /**
     * Listado de inscripciones. Solo administracion.
     *
     * {@code ?canceladas=true} devuelve el historico (baja logica) con el motivo y
     * quien/cuando las cancelo; por defecto, las activas.
     */
    @GetMapping
    @PreAuthorize(Roles.VE_INSCRIPCIONES)
    public List<InscripcionListadoDTO> listar(
            @RequestParam(defaultValue = "false") boolean canceladas) {
        return inscripcionService.listarParaTabla(canceladas);
    }

    /**
     * Detalle completo de una inscripcion: datos de la venta, responsables, casetas con
     * su costo, datos de cancelacion y la traza de auditoria del ciclo de vida
     * (quien/cuando/desde donde). Solo administracion.
     */
    @GetMapping("/{id}")
    @PreAuthorize(Roles.VE_INSCRIPCIONES)
    public ResponseEntity<InscripcionDetalleDTO> detalle(@PathVariable Long id) {
        InscripcionDetalleDTO d = inscripcionService.detalleParaTabla(id);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }

    /**
     * Cancela una venta: libera sus casetas (vuelven a 'L' en el mapa, via WebSocket),
     * la pasa al historico de canceladas y deja huella de auditoria.
     *
     * **Quien puede cancelar (V11):** administracion directamente, con motivo obligatorio;
     * el vendedor que registro la venta SOLO despues de que administracion aprobo su
     * solicitud de cancelacion (en ese caso el motivo es el de la solicitud).
     *
     * Codigos: 200 cancelada · 400 motivo vacio o ya estaba cancelada, o vendedor sin
     * solicitud aprobada · 403 ajena · 404 inexistente · 409 no se pudo.
     */
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Map<String, Object>> cancelar(
            @PathVariable Long id,
            @RequestBody(required = false) CancelarInscripcionService.Peticion peticion,
            @RequestHeader(value = "X-Origen", required = false) String origen) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Inscripcion i = inscripcionService.findById(id);
        if (i == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "La venta no existe"));
        }
        if (!usuarioId.equals(i.getRegistroIdUsuario()) && !esAdministracion()) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "mensaje", "Esta venta no es tuya"));
        }

        String motivo = peticion != null ? peticion.motivo() : null;
        CancelarInscripcionService.Resultado r =
                cancelarInscripcion.cancelar(id, motivo, usuarioId, origenDe(origen), esAdministracion());
        if (!r.ok()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("puestosLiberados", r.puestosLiberados());
        return ResponseEntity.ok(cuerpo);
    }

    /**
     * Registra una venta completa: entidad, responsables, inscripcion y casetas, todo en una
     * transaccion. Cualquier usuario autenticado puede vender.
     *
     * Codigos: 200 si se registro · 400 si faltan datos · **409 si alguna caseta dejo de estar
     * disponible** (la SPA distingue por codigo, no por el texto del mensaje).
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> registrar(
            @RequestBody RegistroVentaService.NuevaVenta req,
            @RequestHeader(value = "X-Origen", required = false) String origen) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        // Un vendedor no vende casetas que no tiene asignadas. Hay que comprobarlo tambien aqui y
        // no solo en el carrito: este endpoint recibe la lista de casetas directamente y ocupa las
        // que estén libres, asi que sin esto bastaba con saltarse el carrito para vender cualquiera.
        if (req != null && esVendedor()) {
            List<Long> noPermitidas = vendedorAsignacion.casetasNoPermitidas(usuarioId, req.puestos());
            if (!noPermitidas.isEmpty()) {
                return ResponseEntity.status(403).body(Map.of("ok", false,
                        "mensaje", "Hay casetas que no estan asignadas a ti: " + noPermitidas));
            }
        }
        try {
            RegistroVentaService.Resultado r = registroVenta.registrar(req, usuarioId, origenDe(origen));
            if (!r.ok()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("ok", false, "mensaje", r.mensaje()));
            }
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("ok", true);
            cuerpo.put("mensaje", r.mensaje());
            cuerpo.put("inscripcionId", r.inscripcionId());
            cuerpo.put("puestos", r.puestosOcupados());
            cuerpo.put("total", r.total());
            return ResponseEntity.ok(cuerpo);
        } catch (RegistroVentaService.CasetaNoDisponibleException e) {
            // 409 y no 400: no es que el vendedor mandara mal los datos, es que otro gano la
            // carrera. La venta entera quedo revertida.
            return ResponseEntity.status(409).body(Map.of("ok", false, "mensaje", e.getMessage()));
        } catch (IllegalArgumentException e) {
            // Datos que no cuadran contra la base: p.ej. una opcion de precio que no es de esa
            // categoria. Sin este catch salia un 500 con la traza dentro, que al vendedor no le
            // dice nada y al que lee los registros tampoco.
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false, "mensaje", e.getMessage() == null ? "Datos invalidos" : e.getMessage()));
        }
    }

    /**
     * Ventas propias que siguen sin comprobante de pago, con los dias que llevan asi.
     *
     * Cualquier vendedor ve LAS SUYAS: el id sale del token, nunca de un parametro, igual
     * que en "mis ventas".
     */
    @GetMapping("/mis-pendientes")
    public ResponseEntity<?> misPendientes() {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        return ResponseEntity.ok(registroVenta.pendientesDe(usuarioId));
    }

    /**
     * Adjunta (o reemplaza) el comprobante de pago de una venta propia.
     *
     * Es multipart y no JSON porque viaja un archivo — en el movil, una foto recien tomada.
     * Va aparte del registro a proposito: cerrar la venta asegura la caseta, y el comprobante
     * puede subirse despues, cuando haya señal.
     */
    @PostMapping(value = "/{id}/comprobante", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> adjuntarComprobante(
            @PathVariable Long id,
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(value = "entidadBancaria", required = false) String entidadBancaria,
            @RequestParam(value = "numComprobante", required = false) String numComprobante,
            @RequestHeader(value = "X-Origen", required = false) String origen) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        RegistroVentaService.Resultado r =
                registroVenta.adjuntarComprobante(id, archivo, entidadBancaria, numComprobante, usuarioId, origenDe(origen));
        return r.ok()
                ? ResponseEntity.ok(Map.of("ok", true, "mensaje", r.mensaje()))
                : ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
    }

    /**
     * Recibo de la venta en PDF.
     *
     * Lo puede descargar **quien la registro**, o administracion. Esa comprobacion no existia
     * en el equivalente Thymeleaf ({@code /ver/inscripcion/{id}/recibo.pdf}), donde cualquier
     * usuario con sesion podia bajarse el recibo de cualquier otro vendedor — con los datos
     * del cliente dentro. Aqui no se repite.
     */
    @GetMapping("/{id}/recibo")
    public ResponseEntity<byte[]> recibo(@PathVariable Long id) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return ResponseEntity.status(401).build();

        Inscripcion i = inscripcionService.findById(id);
        if (i == null) return ResponseEntity.notFound().build();
        if (!usuarioId.equals(i.getRegistroIdUsuario()) && !esAdministracion()) {
            return ResponseEntity.status(403).build();
        }

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            reciboPdfService.generarRecibo(id, salida);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    // `inline` y no `attachment`: en el movil interesa verlo antes de guardarlo,
                    // y para descargarlo basta el boton del propio visor.
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=recibo-" + id + ".pdf")
                    .body(salida.toByteArray());
        } catch (Exception e) {
            log.error("No se pudo generar el recibo de la inscripcion {}", id, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Recibo de un responsable extra (credencial adicional) en PDF.
     *
     * Lo puede descargar **quien registró la venta**, o administración. El responsable
     * debe ser "extra" (es_extra = true) y pertenecer a la entidad de la inscripción.
     */
    @GetMapping("/{id}/responsables/{responsableId}/recibo-extra")
    public ResponseEntity<byte[]> reciboResponsableExtra(
            @PathVariable Long id,
            @PathVariable Long responsableId) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return ResponseEntity.status(401).build();

        Inscripcion i = inscripcionService.findById(id);
        if (i == null) return ResponseEntity.notFound().build();
        if (!usuarioId.equals(i.getRegistroIdUsuario()) && !esAdministracion()) {
            return ResponseEntity.status(403).build();
        }

        // Verificar que el responsable pertenezca a esta entidad y sea extra
        var rOpt = responsableDao.findById(responsableId);
        if (rOpt.isEmpty() || !rOpt.get().getEntidad().getId().equals(i.getEntidad().getId()) || !rOpt.get().isEsExtra()) {
            return ResponseEntity.status(404).build();
        }

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            reciboPdfService.generarReciboResponsableExtra(responsableId, salida);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=recibo-extra-" + responsableId + ".pdf")
                    .body(salida.toByteArray());
        } catch (Exception e) {
            log.error("No se pudo generar el recibo extra del responsable {}", responsableId, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Responsables de la venta con el estado de su foto, y si ya estan todas.
     *
     * Es la pantalla de "¿a quien me falta la foto?": en la feria casi nunca se tienen las dos
     * en el momento de vender, y hasta ahora la unica forma de agregarlas despues era rehacer
     * el registro entero desde el sitio viejo.
     */
    @GetMapping("/{id}/responsables")
    public ResponseEntity<Map<String, Object>> responsables(@PathVariable Long id) {
        ResponseEntity<Map<String, Object>> veto = comprobarAcceso(id);
        if (veto != null) return veto;

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("responsables", responsableFoto.listar(id));
        cuerpo.put("fotosCompletas", responsableFoto.fotosCompletas(id));
        return ResponseEntity.ok(cuerpo);
    }

    /**
     * Corrige los datos de la entidad y de su responsable legal.
     *
     * Es PATCH y no PUT a proposito: llegan solo los campos que se tocaron, y un campo ausente
     * se deja como estaba. Con PUT, un formulario que no mande el NIT lo borraria.
     */
    @PatchMapping("/{id}/entidad")
    public ResponseEntity<Map<String, Object>> editarEntidad(
            @PathVariable Long id,
            @RequestBody EdicionVentaService.DatosEntidad datos) {
        ResponseEntity<Map<String, Object>> veto = comprobarAcceso(id);
        if (veto != null) return veto;

        EdicionVentaService.Resultado r = edicionVenta.editarEntidad(id, datos, usuarioActual());
        return r.ok()
                ? ResponseEntity.ok(Map.of("ok", true, "mensaje", r.mensaje()))
                : ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
    }

    /** Corrige los datos de una persona responsable de esta venta. */
    @PatchMapping("/{id}/responsables/{responsableId}")
    public ResponseEntity<Map<String, Object>> editarResponsable(
            @PathVariable Long id,
            @PathVariable Long responsableId,
            @RequestBody EdicionVentaService.DatosResponsable datos) {
        ResponseEntity<Map<String, Object>> veto = comprobarAcceso(id);
        if (veto != null) return veto;

        EdicionVentaService.Resultado r =
                edicionVenta.editarResponsable(id, responsableId, datos, usuarioActual());
        return r.ok()
                ? ResponseEntity.ok(Map.of("ok", true, "mensaje", r.mensaje()))
                : ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
    }

    /**
     * Todo lo que la ficha de una venta necesita, en UNA peticion.
     *
     * Antes hacian falta tres —datos, casetas y responsables— y en un telefono con la red de la
     * feria eso son tres esperas antes de poder mirar nada. Aqui no hay consulta nueva: se
     * juntan las que ya existian.
     */
    @GetMapping("/{id}/detalle")
    public ResponseEntity<Map<String, Object>> ficha(@PathVariable Long id) {
        ResponseEntity<Map<String, Object>> veto = comprobarAcceso(id);
        if (veto != null) return veto;

        Inscripcion i = inscripcionService.findById(id);
        if (i == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "La venta no existe"));
        }
        var e = i.getEntidad();

        Map<String, Object> entidad = new LinkedHashMap<>();
        entidad.put("nombre", e == null ? "" : e.getNombre());
        entidad.put("nit", e == null ? "" : e.getNit());
        entidad.put("descripcion", e == null ? "" : e.getDescripcion());
        entidad.put("tipo", e == null || e.getTipoEntidad() == null ? "" : e.getTipoEntidad().getNombre());
        entidad.put("representanteLegal", e == null ? "" : e.getRepresentanteLegal());
        entidad.put("ciRepresentante", e == null ? "" : e.getCiRepresentante());
        entidad.put("celularRepresentante", e == null ? "" : e.getCelularRepresentante());

        Map<String, Object> pago = new LinkedHashMap<>();
        pago.put("contado", i.isPagoContado());
        pago.put("entidadBancaria", i.getEntidadBancaria());
        pago.put("numComprobante", i.getNumComprobante());
        // El comprobante hace falta SIEMPRE, tambien al contado: es lo que decide si se puede
        // emitir la credencial. La ficha tiene que decirlo sin que haya que deducirlo.
        pago.put("conComprobante", i.getImgComprobante() != null && !i.getImgComprobante().isBlank());
        pago.put("comprobanteUrl", i.getImgComprobante() == null || i.getImgComprobante().isBlank()
                ? null : "/files/" + i.getImgComprobante());

        List<Map<String, Object>> casetas = i.getInscripcionPuestos().stream()
                .filter(ip -> ip.getPuesto() != null
                        && !"X".equalsIgnoreCase(String.valueOf(ip.getEstado())))
                .map(ip -> {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("codigo", ip.getPuesto().getCodigo());
                    c.put("categoria", ip.getPuesto().getCategoria() == null
                            ? "" : ip.getPuesto().getCategoria().getNombre());
                    c.put("color", ip.getPuesto().getCategoria() == null
                            ? null : ip.getPuesto().getCategoria().getColor());
                    return c;
                })
                .toList();

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("id", i.getId());
        cuerpo.put("entidad", entidad);
        cuerpo.put("pago", pago);
        cuerpo.put("casetas", casetas);
        cuerpo.put("responsables", responsableFoto.listar(id));
        cuerpo.put("fotosCompletas", responsableFoto.fotosCompletas(id));
        return ResponseEntity.ok(cuerpo);
    }

    /** Sube o reemplaza la foto de un responsable de esta venta. */
    @PostMapping(value = "/{id}/responsables/{responsableId}/foto",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> subirFotoResponsable(
            @PathVariable Long id,
            @PathVariable Long responsableId,
            @RequestPart("archivo") MultipartFile archivo) {
        ResponseEntity<Map<String, Object>> veto = comprobarAcceso(id);
        if (veto != null) return veto;

        ResponsableFotoService.Resultado r =
                responsableFoto.guardar(id, responsableId, archivo, usuarioActual());
        return respuestaFoto(r);
    }

    @DeleteMapping("/{id}/responsables/{responsableId}/foto")
    public ResponseEntity<Map<String, Object>> quitarFotoResponsable(
            @PathVariable Long id, @PathVariable Long responsableId) {
        ResponseEntity<Map<String, Object>> veto = comprobarAcceso(id);
        if (veto != null) return veto;

        return respuestaFoto(responsableFoto.quitar(id, responsableId, usuarioActual()));
    }

    private ResponseEntity<Map<String, Object>> respuestaFoto(ResponsableFotoService.Resultado r) {
        // LinkedHashMap y no Map.of: `responsable` es null cuando falla, y Map.of lanza NPE
        // con un valor null.
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("responsable", r.responsable());
        return r.ok() ? ResponseEntity.ok(cuerpo) : ResponseEntity.badRequest().body(cuerpo);
    }

    /**
     * Todos los responsables EXTRA de la feria, con su cobro y su comprobante.
     *
     * Va aqui, y no dentro de la ficha de cada venta, porque la pregunta que contesta es de toda
     * la feria: "¿quien se agrego de mas y pago?". Con cien ventas, responderla abriendo una por
     * una no lo hace nadie — y por eso los cobros de 15 Bs se perdian de vista.
     *
     * Mismo permiso que el listado global de inscripciones: enseña un SUBCONJUNTO de lo que esas
     * pantallas ya muestran, asi que negarlo aqui seria arbitrario.
     */
    @GetMapping("/responsables-extra")
    @PreAuthorize(Roles.VE_INSCRIPCIONES)
    public List<ResponsableExtraService.ExtraListado> responsablesExtra() {
        return responsableExtra.listarExtras();
    }

    /**
     * Cuantos responsables admite esta venta y cuantos tiene ya.
     *
     * Lo pide la ficha de la venta ANTES de abrir el formulario, para saber si lo que va a
     * agregar es gratis o lleva cobro: decirselo despues de que ha escrito los datos es
     * hacerle perder el trabajo, y con el cliente delante.
     */
    @GetMapping("/{id}/responsables/cupo")
    public ResponseEntity<?> cupoResponsables(@PathVariable Long id) {
        ResponseEntity<Map<String, Object>> veto = comprobarAcceso(id);
        if (veto != null) return veto;
        ResponsableExtraService.Cupo c = responsableExtra.cupoDe(id);
        if (c == null) return ResponseEntity.status(404).body(Map.of("ok", false));
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "casetas", c.casetas(),
                "derecho", c.derecho(),
                "registrados", c.registrados(),
                "extras", c.extras(),
                "dentroDelDerecho", c.dentroDelDerecho(),
                "costoSiguiente", c.costoSiguiente()));
    }

    /**
     * Agrega un responsable a una venta ya registrada.
     *
     * Es multipart porque puede traer el comprobante del cobro. Los que pasan del derecho que
     * dan las casetas (2 por caseta) se cobran, y entonces el comprobante NO es opcional: el
     * servicio lo rechaza sin el. Va en la misma peticion que crea al responsable para que no
     * exista el estado intermedio "creado y sin pagar", que es donde se pierden los cobros.
     *
     * La foto se sube despues, con el endpoint de siempre: hasta que no existe el responsable
     * no hay id al que asociarla.
     */
    @PostMapping(value = "/{id}/responsables", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> agregarResponsable(
            @PathVariable Long id,
            @RequestParam("nombre") String nombre,
            @RequestParam(value = "paterno", required = false) String paterno,
            @RequestParam(value = "materno", required = false) String materno,
            @RequestParam("ci") String ci,
            @RequestParam(value = "correo", required = false) String correo,
            @RequestParam(value = "celular", required = false) String celular,
            @RequestPart(value = "comprobante", required = false) MultipartFile comprobante) {
        ResponseEntity<Map<String, Object>> veto = comprobarAcceso(id);
        if (veto != null) return veto;
        Long usuarioId = usuarioActual();
        ResponsableExtraService.Resultado r = responsableExtra.agregar(id,
                new ResponsableExtraService.NuevoResponsable(nombre, paterno, materno, ci, correo, celular),
                comprobante, usuarioId);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("responsableId", r.responsableId());
        cuerpo.put("cobrado", r.cobrado());
        cuerpo.put("monto", r.monto());
        return r.ok() ? ResponseEntity.ok(cuerpo) : ResponseEntity.badRequest().body(cuerpo);
    }

    /**
     * La venta tiene que existir y ser de quien pregunta (o de administracion). Devuelve la
     * respuesta de rechazo, o null si puede pasar. Es la misma regla del recibo: los datos de
     * los responsables —nombre, C.I., foto— son del cliente de OTRO vendedor.
     */
    private ResponseEntity<Map<String, Object>> comprobarAcceso(Long inscripcionId) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Inscripcion i = inscripcionService.findById(inscripcionId);
        if (i == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "La venta no existe"));
        }
        // El dueño de la venta, administracion, o quien acredita. Lo ultimo hace falta porque
        // la foto que falta se toma muchas veces DELANTE del verificador, con el expositor ahi
        // mismo esperando su credencial; mandarlo de vuelta a su vendedor detendria la cola.
        if (!usuarioId.equals(i.getRegistroIdUsuario()) && !esAdministracion() && !esVerificador()) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "mensaje", "Esta venta no es tuya"));
        }
        return null;
    }

    private boolean esVerificador() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_VERIFICADOR"::equals);
    }

    /** Id del usuario del token, o null si no hay sesion valida. */
    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }

    /**
     * De donde llega la peticion, para la auditoria: WEB por defecto, o APK si el
     * cliente (el futuro empaquetado Capacitor) envia el header {@code X-Origen}.
     */
    /** ¿Quien vende es un vendedor? Administracion no se filtra por asignaciones. */
    private boolean esVendedor() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMINISTRATIVO".equals(a.getAuthority()));
    }

    private static String origenDe(String origen) {
        return AuditoriaService.ORIGEN_APK.equalsIgnoreCase(origen == null ? "" : origen.trim())
                ? AuditoriaService.ORIGEN_APK
                : AuditoriaService.ORIGEN_WEB;
    }

    /** ¿El usuario del token pertenece a administracion? (ver Roles.AUTORIDADES_ADMINISTRA) */
    private boolean esAdministracion() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return false;
        return a.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Roles.AUTORIDADES_ADMINISTRA::contains);
    }
}
