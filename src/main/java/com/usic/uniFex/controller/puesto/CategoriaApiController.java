package com.usic.uniFex.controller.puesto;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.CategoriaOpcion;
import com.usic.uniFex.model.service.CategoriaMapaService;
import com.usic.uniFex.model.service.CategoriaOpcionService;
import com.usic.uniFex.model.service.CategoriaMapaService.ResultadoAjuste;
import com.usic.uniFex.model.service.PuestoEventPublisher;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Alta, edicion, ajuste de cantidad y baja de categorias para el diseñador de plano (Fase 3).
 *
 * Rediseñar el plano no es una operacion de venta: crear una categoria crea N casetas, y
 * cambiarle el color, la forma o el tamaño altera lo que ven todos los vendedores. Por eso este
 * controlador entero se reserva a la administracion, no a cualquier usuario autenticado.
 */
@RestController
@RequestMapping("/api/app/categorias")
@RequiredArgsConstructor
@PreAuthorize(Roles.EDITA_PLANO)
public class CategoriaApiController {

    private final CategoriaMapaService service;
    private final CategoriaOpcionService opciones;
    private final PuestoEventPublisher publisher;
    private final IPuestoDao puestoDao;

    /**
     * Las categorias vivas del plano.
     *
     * Faltaba: este controlador solo tenia POST, PATCH y DELETE, asi que un GET a
     * /api/app/categorias no lo atendia nadie, caia en /error y volvia al cliente como un 302 al
     * login. La pantalla de Vendedores lo pedia para llenar su desplegable y se quedaba vacio
     * sin dar ningun error entendible.
     *
     * El Editor no lo necesita porque saca las categorias de la propia lista de casetas, pero eso
     * solo muestra las que YA tienen casetas: para asignar una categoria recien creada hace falta
     * la lista de verdad.
     */
    /*
     * Lo LEE cualquier usuario autenticado, no solo administracion: el formulario de venta
     * necesita las opciones de precio para poder elegir una, y quien vende es precisamente
     * quien no edita el plano. Escribir sigue reservado (el @PreAuthorize de la clase).
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> listar() {
        Map<Long, List<CategoriaOpcion>> porCategoria = opciones.porCategoria();
        return service.listar().stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("nombre", c.getNombre());
                    m.put("color", c.getColor());
                    m.put("forma", c.getForma());
                    m.put("tamanoMapa", c.getTamanoMapa());
                    m.put("precioBase", c.getPrecioBase());
                    m.put("opciones", porCategoria.getOrDefault(c.getId(), List.of()).stream()
                            .map(CategoriaApiController::opcionComoMapa).toList());
                    return m;
                })
                .toList();
    }

    private static Map<String, Object> opcionComoMapa(CategoriaOpcion o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("nombre", o.getNombre());
        m.put("precio", o.getPrecio());
        m.put("predeterminada", Boolean.TRUE.equals(o.getPredeterminada()));
        m.put("orden", o.getOrden());
        return m;
    }

    // ===== Opciones de precio =====

    /**
     * Agrega una opcion de precio a la categoria.
     *
     * Si pasa a ser la predeterminada hay que REDIFUNDIR las casetas: el mapa pinta el precio de
     * cada caseta en su ficha, y ese precio sale de la predeterminada. Sin la difusion, los demas
     * mapas seguirian cantando el precio viejo hasta recargar — con un cliente delante.
     */
    @PostMapping("/{id}/opciones")
    public ResponseEntity<Map<String, Object>> crearOpcion(
            @PathVariable Long id, @RequestBody CategoriaOpcionService.CambioOpcion req) {
        try {
            CategoriaOpcion o = opciones.crear(id, req, usuarioActual());
            if (Boolean.TRUE.equals(o.getPredeterminada())) publisher.publicarVarios(puestoDao.idsActivosDeCategoria(id));
            return ResponseEntity.ok(Map.<String, Object>of("ok", true, "opcion", opcionComoMapa(o)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.<String, Object>of("ok", false, "mensaje", e.getMessage()));
        }
    }

    /** Cambia nombre, precio, orden o cual es la predeterminada. Campos nulos: no se tocan. */
    @PatchMapping("/{id}/opciones/{opcionId}")
    public ResponseEntity<Map<String, Object>> actualizarOpcion(
            @PathVariable Long id, @PathVariable Long opcionId,
            @RequestBody CategoriaOpcionService.CambioOpcion req) {
        CategoriaOpcion o = opciones.actualizar(opcionId, req, usuarioActual());
        if (o == null) return ResponseEntity.status(404).body(Map.<String, Object>of("ok", false));
        if (Boolean.TRUE.equals(o.getPredeterminada())) publisher.publicarVarios(puestoDao.idsActivosDeCategoria(id));
        return ResponseEntity.ok(Map.<String, Object>of("ok", true, "opcion", opcionComoMapa(o)));
    }

    /** Baja logica. Una opcion ya usada en ventas nunca se borra: el recibo tiene que seguir leyendose. */
    @DeleteMapping("/{id}/opciones/{opcionId}")
    public ResponseEntity<Map<String, Object>> eliminarOpcion(
            @PathVariable Long id, @PathVariable Long opcionId) {
        String motivo = opciones.eliminar(opcionId, usuarioActual());
        if (motivo == null) return ResponseEntity.status(404).body(Map.<String, Object>of("ok", false));
        if (!motivo.isEmpty()) {
            return ResponseEntity.status(409).body(Map.<String, Object>of("ok", false, "mensaje", motivo));
        }
        return ResponseEntity.ok(Map.<String, Object>of("ok", true));
    }

    /** Crea la categoria y sus N casetas, y las difunde para que aparezcan en los mapas abiertos. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody CategoriaMapaService.NuevaCategoria req) {
        Categoria c = service.crear(req, usuarioActual());
        // Toda categoria nace con su opcion predeterminada, con su mismo nombre y su precio.
        // Sin ella el formulario de venta no tendria nada que elegir y el precio caeria al
        // calculo de respaldo, que para una categoria nueva da CERO: se venderia gratis.
        opciones.asegurarPredeterminada(c.getId(), c.getNombre(), c.getPrecioBase(), usuarioActual());
        publisher.publicarVarios(puestoDao.idsActivosDeCategoria(c.getId()));
        return ResponseEntity.ok(Map.<String, Object>of("ok", true, "id", c.getId(), "nombre", c.getNombre()));
    }

    /**
     * Cambia nombre/color/forma/tamaño. La apariencia de una caseta la hereda de su categoria,
     * asi que hay que redifundir todas sus casetas o los otros mapas seguirian pintandolas
     * con el color viejo.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(
            @PathVariable Long id, @RequestBody CategoriaMapaService.CambioCategoria cambio) {
        Categoria c = service.actualizar(id, cambio);
        if (c == null) return ResponseEntity.status(404).body(Map.<String, Object>of("ok", false));
        publisher.publicarVarios(puestoDao.idsActivosDeCategoria(id));
        return ResponseEntity.ok(Map.<String, Object>of("ok", true));
    }

    /**
     * Ajusta la cantidad de casetas de la categoria (subir crea, bajar anula las libres de codigo
     * mas alto). Difunde las creadas y las anuladas. Si al bajar quedan casetas vendidas que no se
     * pudieron quitar, lo informa en {@code noQuitadas} — no es un error, es informacion.
     */
    @PatchMapping("/{id}/cantidad")
    public ResponseEntity<Map<String, Object>> ajustarCantidad(
            @PathVariable Long id, @RequestBody CantidadRequest req) {
        if (req.cantidad() == null || req.cantidad() < 0) {
            return ResponseEntity.status(400).body(Map.<String, Object>of("ok", false, "mensaje", "Cantidad invalida"));
        }
        ResultadoAjuste r = service.ajustarCantidad(id, req.cantidad(), usuarioActual());
        if (r == null) return ResponseEntity.status(404).body(Map.<String, Object>of("ok", false, "mensaje", "Categoria inexistente"));
        publisher.publicarVarios(r.afectados());
        return ResponseEntity.ok(Map.<String, Object>of(
                "ok", true, "creadas", r.creadas(), "anuladas", r.anuladas(), "noQuitadas", r.noQuitadas()));
    }

    /**
     * Elimina (baja logica) la categoria entera. Solo si ninguna de sus casetas tiene ventas ni
     * esta ocupada/reservada; si no, 409 sin tocar nada.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable Long id) {
        List<Long> anuladas = service.eliminar(id, usuarioActual());
        if (anuladas == null) {
            return ResponseEntity.status(409).body(Map.<String, Object>of(
                    "ok", false, "mensaje", "No se puede eliminar: la categoria tiene casetas vendidas o reservadas"));
        }
        publisher.publicarVarios(anuladas); // llegan con activo=false: los clientes las quitan
        return ResponseEntity.ok(Map.<String, Object>of("ok", true, "eliminadas", anuladas.size()));
    }

    public record CantidadRequest(Integer cantidad) {
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
    }
}
