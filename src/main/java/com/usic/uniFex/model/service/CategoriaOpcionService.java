package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.ICategoriaOpcionDao;
import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.CategoriaOpcion;
import com.usic.uniFex.model.IService.ICategoriaService;

import lombok.RequiredArgsConstructor;

/**
 * Las opciones de precio de cada categoria.
 *
 * Dos reglas que no pueden romperse y por eso viven aqui y no en el controlador:
 *
 * 1. **Siempre hay exactamente una opcion predeterminada viva por categoria.** Es la que se
 *    aplica cuando nadie elige, asi que una categoria sin ella dejaria ventas sin precio. Marcar
 *    una nueva desmarca la anterior en la misma transaccion; la predeterminada no se puede
 *    borrar mientras haya otras (hay que nombrar sucesora antes).
 *
 * 2. **El precio de la predeterminada se copia a `categoria.precio_base`.** Esa columna la leen
 *    la funcion almacenada `obtenercostopuesto` y el mapa, que no saben de esta tabla. Si se
 *    dejaran desincronizados, el mapa cantaria un precio y la venta cobraria otro.
 *
 * Una opcion con ventas registradas NO se borra: se da de baja logica. El recibo de una venta
 * de hace un mes tiene que poder seguir diciendo por que costo lo que costo.
 */
@Service
@RequiredArgsConstructor
public class CategoriaOpcionService {

    private final ICategoriaOpcionDao dao;
    private final ICategoriaService categoriaService;

    /** Alta o cambio de una opcion. Los campos nulos no se tocan (semantica PATCH). */
    public record CambioOpcion(String nombre, BigDecimal precio, Boolean predeterminada, Integer orden) {
    }

    @Transactional(readOnly = true)
    public List<CategoriaOpcion> listar(Long categoriaId) {
        return dao.vivasDe(categoriaId);
    }

    /** Todas las opciones vivas agrupadas por categoria: una consulta para todo el catalogo. */
    @Transactional(readOnly = true)
    public Map<Long, List<CategoriaOpcion>> porCategoria() {
        return dao.todasVivas().stream()
                .collect(Collectors.groupingBy(o -> o.getCategoria().getId(),
                        java.util.LinkedHashMap::new, Collectors.toList()));
    }

    /**
     * Le da a una categoria su opcion predeterminada si todavia no tiene ninguna.
     *
     * La llama el alta de categorias. Sin esto, una categoria recien creada no tenia NINGUNA
     * opcion: el formulario de venta no ofrecia nada que elegir y la venta caia al calculo de
     * respaldo, que para una categoria nueva da cero. Es decir, se vendia gratis y sin avisar.
     * La migracion sembro las que ya existian; esto cubre las que se creen de aqui en adelante.
     */
    @Transactional
    public CategoriaOpcion asegurarPredeterminada(Long categoriaId, String nombre,
                                                  BigDecimal precio, Long usuarioId) {
        if (!dao.vivasDe(categoriaId).isEmpty()) return null;
        return crear(categoriaId, new CambioOpcion(nombre, precio, true, 0), usuarioId);
    }

    @Transactional
    public CategoriaOpcion crear(Long categoriaId, CambioOpcion req, Long usuarioId) {
        Categoria c = categoriaService.findById(categoriaId);
        if (c == null) throw new IllegalArgumentException("La categoria no existe");
        String nombre = limpio(req.nombre());
        if (nombre == null) throw new IllegalArgumentException("La opcion necesita un nombre");

        CategoriaOpcion o = new CategoriaOpcion();
        o.setCategoria(c);
        o.setNombre(nombre);
        o.setPrecio(req.precio() == null ? BigDecimal.ZERO : req.precio());
        o.setOrden(req.orden() == null ? siguienteOrden(categoriaId) : req.orden());
        o.setPredeterminada(false);
        o.setRegistro(new Date());
        o.setRegistroIdUsuario(usuarioId);
        o.setEstado(CategoriaOpcion.REGISTRO_ACTIVO);
        // La primera opcion de una categoria es forzosamente la predeterminada: si no, la
        // categoria se quedaria sin precio por defecto.
        boolean primera = dao.vivasDe(categoriaId).isEmpty();
        o = dao.save(o);
        if (primera || Boolean.TRUE.equals(req.predeterminada())) marcarPredeterminada(categoriaId, o.getId());
        return o;
    }

    @Transactional
    public CategoriaOpcion actualizar(Long id, CambioOpcion req, Long usuarioId) {
        CategoriaOpcion o = dao.findById(id).orElse(null);
        if (o == null || CategoriaOpcion.REGISTRO_ANULADO.equals(o.getEstado())) return null;
        if (limpio(req.nombre()) != null) o.setNombre(limpio(req.nombre()));
        if (req.precio() != null) o.setPrecio(req.precio());
        if (req.orden() != null) o.setOrden(req.orden());
        o.setModificacion(new Date());
        o.setModificacionIdUsuario(usuarioId);
        o = dao.save(o);
        if (Boolean.TRUE.equals(req.predeterminada())) {
            marcarPredeterminada(o.getCategoria().getId(), o.getId());
        } else if (Boolean.TRUE.equals(o.getPredeterminada())) {
            // Cambio el precio de la predeterminada: la copia de `categoria` tiene que seguirlo.
            sincronizarPrecioBase(o.getCategoria().getId(), o.getPrecio());
        }
        return o;
    }

    /**
     * Deja esta como la unica predeterminada de su categoria.
     *
     * El desmarcado va ANTES del marcado: el indice unico de V33 solo admite una viva, asi que
     * marcar primero chocaria contra el indice con la anterior todavia puesta.
     */
    @Transactional
    public void marcarPredeterminada(Long categoriaId, Long opcionId) {
        for (CategoriaOpcion otra : dao.vivasDe(categoriaId)) {
            if (!otra.getId().equals(opcionId) && Boolean.TRUE.equals(otra.getPredeterminada())) {
                otra.setPredeterminada(false);
                dao.save(otra);
            }
        }
        dao.flush();
        CategoriaOpcion o = dao.findById(opcionId).orElseThrow();
        o.setPredeterminada(true);
        dao.save(o);
        sincronizarPrecioBase(categoriaId, o.getPrecio());
    }

    /**
     * Baja logica de una opcion.
     *
     * @return null si no existe; un texto con el motivo si no se puede; "" si se dio de baja.
     */
    @Transactional
    public String eliminar(Long id, Long usuarioId) {
        CategoriaOpcion o = dao.findById(id).orElse(null);
        if (o == null || CategoriaOpcion.REGISTRO_ANULADO.equals(o.getEstado())) return null;
        Long categoriaId = o.getCategoria().getId();
        List<CategoriaOpcion> vivas = dao.vivasDe(categoriaId);
        if (vivas.size() <= 1) {
            return "Una categoria no puede quedarse sin ninguna opcion de precio";
        }
        if (Boolean.TRUE.equals(o.getPredeterminada())) {
            return "Esta es la opcion predeterminada: marca otra como predeterminada antes de quitarla";
        }
        o.setEstado(CategoriaOpcion.REGISTRO_ANULADO);
        o.setModificacion(new Date());
        o.setModificacionIdUsuario(usuarioId);
        dao.save(o);
        return "";
    }

    /**
     * La opcion que se le aplica a una categoria en una venta.
     *
     * Se valida CONTRA LA BASE que la opcion pedida sea de esa categoria: el id lo escribe el
     * cliente, y sin esta comprobacion se podria pagar una caseta de PYMES al precio de la
     * opcion mas barata de otra categoria. Si no se pide ninguna, se usa la predeterminada.
     */
    @Transactional(readOnly = true)
    public CategoriaOpcion resolverParaVenta(Long categoriaId, Long opcionPedida) {
        if (opcionPedida != null) {
            CategoriaOpcion o = dao.findById(opcionPedida).orElse(null);
            if (o != null && !CategoriaOpcion.REGISTRO_ANULADO.equals(o.getEstado())
                    && o.getCategoria() != null && categoriaId.equals(o.getCategoria().getId())) {
                return o;
            }
            throw new IllegalArgumentException("La opcion de precio elegida no es de esa categoria");
        }
        return dao.predeterminadaDe(categoriaId).orElse(null);
    }

    private void sincronizarPrecioBase(Long categoriaId, BigDecimal precio) {
        Categoria c = categoriaService.findById(categoriaId);
        if (c == null) return;
        c.setPrecioBase(precio == null ? BigDecimal.ZERO : precio);
        c.setModificacion(new Date());
        categoriaService.save(c);
    }

    private int siguienteOrden(Long categoriaId) {
        return dao.vivasDe(categoriaId).stream()
                .mapToInt(o -> o.getOrden() == null ? 0 : o.getOrden())
                .max().orElse(-1) + 1;
    }

    private static String limpio(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
