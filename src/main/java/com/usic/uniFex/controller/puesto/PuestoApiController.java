package com.usic.uniFex.controller.puesto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.dto.PuestoEstadoDTO;
import com.usic.uniFex.model.dto.PuestoFotoDTO;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.service.PuestoEventPublisher;
import com.usic.uniFex.model.service.PuestoFotoService;
import com.usic.uniFex.model.service.PuestoMapaService;
import com.usic.uniFex.model.service.PuestoReservaService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * API REST de puestos para la SPA/app movil (Fase 2). Protegida por JWT: el usuario
 * se toma del token (SecurityContext), no de la sesion. Cada escritura exitosa
 * difunde el nuevo estado por WebSocket para actualizar el mapa en vivo.
 *
 * Vender (reservar/liberar/confirmar) lo puede hacer cualquier usuario autenticado —los 35
 * vendedores tienen rol ADMINISTRATIVO—. Rediseñar el plano (posiciones, alta y baja de
 * casetas) es solo de la administracion: ver {@link Roles#EDITA_PLANO}.
 */
@RestController
@RequestMapping("/api/app/puestos")
@RequiredArgsConstructor
public class PuestoApiController {

    private final PuestoReservaService reservaService;
    private final PuestoEventPublisher publisher;
    private final PuestoMapaService mapaService;
    private final PuestoFotoService fotoService;
    private final IPuestoDao puestoDao;

    /** Estado de todas las casetas no anuladas (opcionalmente filtrado por categoria). */
    @GetMapping
    public List<PuestoEstadoDTO> listar(@RequestParam(value = "categoriaId", required = false) Long categoriaId) {
        return puestoDao.listarActivos().stream()
                .filter(p -> categoriaId == null
                        || (p.getCategoria() != null && categoriaId.equals(p.getCategoria().getId())))
                .map(PuestoEstadoDTO::de)
                .toList();
    }

    // ===== Venta: cualquier usuario autenticado =====

    @PostMapping("/{id}/reservar")
    public ResponseEntity<Map<String, Object>> reservar(@PathVariable Long id) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();
        boolean ok = reservaService.reservar(id, usuarioId);
        if (ok) publisher.publicar(id);
        return respuesta(ok, ok ? "Caseta reservada" : "La caseta ya no esta disponible");
    }

    @PostMapping("/{id}/liberar")
    public ResponseEntity<Map<String, Object>> liberar(@PathVariable Long id) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();
        boolean ok = reservaService.liberar(id, usuarioId);
        if (ok) publisher.publicar(id);
        return respuesta(ok, ok ? "Reserva liberada" : "No tienes esta caseta reservada");
    }

    // ===== Carrito: varias casetas para una misma venta =====

    /** Ids de casetas de una operacion en lote. */
    public record LotePuestos(List<Long> ids) {
    }

    /**
     * Las casetas que este vendedor tiene en tramite: su carrito.
     *
     * No hace falta guardarlo en ningun sitio aparte — el carrito **es** la reserva. Asi
     * sobrevive a cerrar el movil o a un corte de señal, y no aparece en los listados ni en
     * los reportes, que solo cuentan casetas ya vendidas.
     */
    @GetMapping("/mi-carrito")
    public ResponseEntity<Map<String, Object>> miCarrito() {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();

        List<PuestoEstadoDTO> items = reservaService.carritoDe(usuarioId).stream()
                .map(PuestoEstadoDTO::de).toList();
        BigDecimal total = items.stream()
                .map(i -> i.precio() != null ? i.precio() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("cantidad", items.size());
        cuerpo.put("total", total);
        cuerpo.put("items", items);
        return ResponseEntity.ok(cuerpo);
    }

    /**
     * Suma varias casetas al carrito. Devuelve 200 aunque alguna se rechace: el vendedor
     * necesita quedarse con las que si consiguio y ver cuales perdio, no perderlo todo.
     * El campo {@code rechazadas} es el que hay que mirar.
     */
    @PostMapping("/carrito")
    public ResponseEntity<Map<String, Object>> agregarAlCarrito(@RequestBody LotePuestos req) {
        return operarCarrito(req, true);
    }

    /** Quita varias casetas del carrito y las devuelve a libres. */
    @DeleteMapping("/carrito")
    public ResponseEntity<Map<String, Object>> quitarDelCarrito(@RequestBody LotePuestos req) {
        return operarCarrito(req, false);
    }

    private ResponseEntity<Map<String, Object>> operarCarrito(LotePuestos req, boolean agregar) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();
        if (req == null || req.ids() == null || req.ids().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "mensaje", "No se indico ninguna caseta"));
        }

        PuestoReservaService.ResultadoLote r = agregar
                ? reservaService.agregarAlCarrito(req.ids(), usuarioId)
                : reservaService.quitarDelCarrito(req.ids(), usuarioId);

        // Solo se difunde lo que de verdad cambio de estado.
        publisher.publicarVarios(r.logradas());

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.todoOk());
        cuerpo.put("logradas", r.logradas());
        cuerpo.put("rechazadas", r.rechazadas());
        cuerpo.put("mensaje", r.todoOk()
                ? (agregar ? "Casetas agregadas" : "Casetas liberadas")
                : (agregar
                    ? "Algunas casetas ya no estaban disponibles: " + r.rechazadas().size()
                    : "Algunas casetas no eran tuyas: " + r.rechazadas().size()));
        return ResponseEntity.ok(cuerpo);
    }

    // ===== Fotos de la caseta =====

    /**
     * Fotos de una caseta. Las ve cualquier vendedor: es lo que le enseña al cliente.
     */
    @GetMapping("/{id}/fotos")
    public List<PuestoFotoDTO> fotos(@PathVariable Long id) {
        return fotoService.fotosDe(id).stream().map(PuestoFotoDTO::de).toList();
    }

    /**
     * Ids de las casetas que tienen alguna foto.
     *
     * Se pide de una vez, y no caseta por caseta, porque el mapa dibuja ~500 a la vez: una
     * consulta por pin seria N+1 puro. Con esto el mapa marca cuales se pueden enseñar.
     */
    @GetMapping("/con-foto")
    public List<Long> idsConFoto() {
        return fotoService.idsConFoto();
    }

    /**
     * Sube una foto y la asocia a una o varias casetas (multipart).
     *
     * Admitir varias es lo que hace la funcionalidad viable: en una feria hay filas de
     * casetas identicas, y subir la misma imagen 40 veces no lo hace nadie. El archivo se
     * guarda una sola vez y las casetas comparten la ruta.
     *
     * Es del Editor, o sea administracion: colocar y documentar el plano es trabajo de montaje.
     */
    @PostMapping(value = "/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> subirFoto(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam("puestos") List<Long> puestos,
            @RequestParam(value = "descripcion", required = false) String descripcion) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();

        PuestoFotoService.Resultado r = fotoService.subir(puestos, archivo, descripcion, usuarioId);
        return r.ok()
                ? ResponseEntity.ok(Map.of("ok", true, "mensaje", r.mensaje(),
                        "puestos", r.puestosAfectados()))
                : ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
    }

    @DeleteMapping("/fotos/{fotoId}")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> borrarFoto(@PathVariable Long fotoId) {
        boolean ok = fotoService.borrar(fotoId);
        return respuesta(ok, ok ? "Foto eliminada" : "La foto no existe");
    }

    /** Referencia en texto de donde esta la caseta ("frente a la puerta 3"). */
    public record ReferenciaRequest(String referencia) {
    }

    @PatchMapping("/{id}/referencia")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> referencia(
            @PathVariable Long id, @RequestBody ReferenciaRequest req) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();
        boolean ok = mapaService.cambiarReferencia(id, req.referencia(), usuarioId);
        if (ok) publisher.publicar(id);
        return respuesta(ok, ok ? "Referencia guardada" : "La caseta no existe o esta anulada");
    }

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<Map<String, Object>> confirmar(@PathVariable Long id) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();
        boolean ok = reservaService.confirmar(id, usuarioId);
        if (ok) publisher.publicar(id);
        return respuesta(ok, ok ? "Venta confirmada" : "No se pudo confirmar (reserva no vigente)");
    }

    // ===== Editor del plano: solo administracion =====

    /**
     * Guarda un lote de posiciones y escalas. Una posicion con {@code x}/{@code y} nulos quita
     * la caseta del plano sin borrarla. Difunde cada caseta que cambio: mover una caseta altera
     * lo que ven todos los mapas abiertos.
     */
    @PostMapping("/posiciones")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> guardarPosiciones(
            @RequestBody List<PuestoMapaService.Posicion> posiciones) {
        List<Long> cambiados = mapaService.guardarPosiciones(posiciones);
        publisher.publicarVarios(cambiados);
        return ResponseEntity.ok(Map.<String, Object>of("ok", true, "guardadas", cambiados.size()));
    }

    /** Alta de una caseta suelta dentro de una categoria. */
    @PostMapping
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> crear(@RequestBody NuevaCaseta req) {
        Puesto p = mapaService.crear(req.categoriaId(), req.codigo(), req.tamano(), usuarioActual());
        if (p == null) {
            return ResponseEntity.status(404).body(Map.<String, Object>of("ok", false, "mensaje", "Categoria inexistente"));
        }
        publisher.publicar(p.getId());
        return ResponseEntity.ok(Map.<String, Object>of("ok", true, "id", p.getId()));
    }

    /**
     * Baja logica de una caseta. Solo si esta libre y no arrastra ventas; si no, 409.
     * No se borra la fila: inscripcion_puesto la referencia y hay casetas anuladas con historial.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> anular(@PathVariable Long id) {
        boolean ok = mapaService.anular(id, usuarioActual());
        if (ok) publisher.publicar(id); // llega con activo=false: los clientes la quitan del mapa
        return respuesta(ok, ok ? "Caseta eliminada" : "Solo se puede eliminar una caseta libre y sin ventas");
    }

    public record NuevaCaseta(Long categoriaId, String codigo, String tamano) {
    }

    /**
     * Bloqueo por reparacion (LIBRE -> BLOQUEADO). No es una anulacion: la caseta sigue activa
     * y se ve gris en el mapa, pero no se puede reservar, liberar ni confirmar. Solo se bloquea
     * una caseta libre: nunca roba una reserva en curso (T) ni una venta (O).
     */
    @PostMapping("/{id}/bloquear")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> bloquear(@PathVariable Long id) {
        boolean ok = mapaService.bloquear(id, usuarioActual());
        if (ok) publisher.publicar(id);
        return respuesta(ok, ok ? "Caseta bloqueada (reparacion)" : "Solo se bloquea una caseta libre");
    }

    /**
     * Desbloquea (BLOQUEADO -> LIBRE). Nunca reactiva una caseta anulada: el servicio y el
     * UPDATE condicional rechazan esa vuelta.
     */
    @PostMapping("/{id}/desbloquear")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> desbloquear(@PathVariable Long id) {
        boolean ok = mapaService.desbloquear(id, usuarioActual());
        if (ok) publisher.publicar(id);
        return respuesta(ok, ok ? "Caseta desbloqueada" : "La caseta no esta bloqueada o esta anulada");
    }

    // ===== helpers =====

    private Long usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
    }

    private ResponseEntity<Map<String, Object>> respuesta(boolean ok, String mensaje) {
        return ResponseEntity.status(ok ? 200 : 409)
                .body(Map.<String, Object>of("ok", ok, "mensaje", mensaje));
    }

    private ResponseEntity<Map<String, Object>> noAutenticado() {
        return ResponseEntity.status(401).body(Map.<String, Object>of("ok", false, "mensaje", "No autenticado"));
    }
}
