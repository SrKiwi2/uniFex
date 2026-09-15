package com.usic.uniFex.model.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.IService.ICategoriaService;
import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.Puesto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Colocacion, redimension, alta y baja de casetas sobre el plano (Fase 3, editor). */
@Service
@RequiredArgsConstructor
@Slf4j
public class PuestoMapaService {

    private final IPuestoDao puestoDao;
    private final ICategoriaService categoriaService;

    /**
     * Posicion normalizada (0..1) de una caseta, con su escala y su giro opcionales.
     * {@code x} e {@code y} nulos significan "quitar del plano" (la caseta sigue existiendo).
     * {@code escala} y {@code rotacion} nulos significan "no los cambies".
     */
    public record Posicion(Long id, Double x, Double y, Double escala, Integer rotacion) {
    }

    /** Deja el giro dentro de 0..359. Fuera de ahi dibujaria igual, pero ensucia los datos. */
    private static Integer giroValido(Integer grados) {
        if (grados == null) return null;
        int g = grados % 360;
        return g < 0 ? g + 360 : g;
    }

    /**
     * Guarda un lote de posiciones.
     * @return los ids realmente actualizados, para difundirlos por WebSocket.
     */
    @Transactional
    public List<Long> guardarPosiciones(List<Posicion> posiciones) {
        List<Long> cambiados = new ArrayList<>();
        for (Posicion p : posiciones) {
            if (p.id() == null) continue;
            if (puestoDao.actualizarPosicion(p.id(), p.x(), p.y(), p.escala(), giroValido(p.rotacion())) > 0) {
                cambiados.add(p.id());
            }
        }
        log.info("Editor: {} casetas reposicionadas de {} enviadas", cambiados.size(), posiciones.size());
        return cambiados;
    }

    /**
     * Da de baja logica una caseta. Solo si esta libre y no arrastra ventas: la condicion
     * viaja en el WHERE del UPDATE, no en un if.
     * @return true si se anulo; false si estaba vendida, reservada o ya anulada.
     */
    @Transactional
    public boolean anular(Long puestoId, Long usuarioId) {
        boolean ok = puestoDao.anularSiLibreYSinVentas(puestoId, usuarioId) > 0;
        log.info("Anulacion puesto={} usuario={} -> {}", puestoId, usuarioId, ok ? "OK" : "RECHAZADA");
        return ok;
    }

    /**
     * Bloquea una caseta libre (estado X por reparacion). No es una anulacion: solo cambia
     * estado_puesto, la caseta sigue activa y se ve gris en el mapa sin poder venderse.
     * @return true si se bloqueo; false si no estaba libre.
     */
    @Transactional
    public boolean bloquear(Long puestoId, Long usuarioId) {
        boolean ok = puestoDao.bloquearSiLibre(puestoId, usuarioId) > 0;
        log.info("Bloqueo puesto={} usuario={} -> {}", puestoId, usuarioId, ok ? "OK" : "RECHAZADA");
        return ok;
    }

    /**
     * Guarda la referencia en texto de donde esta la caseta ("frente a la puerta 3").
     *
     * Complementa al plano: el mapa dice donde esta, esto lo dice con palabras que el cliente
     * entiende por telefono. Un texto vacio se guarda como null, para que "sin referencia" sea
     * un unico valor y no dos.
     * @return true si se guardo; false si la caseta no existe o esta anulada.
     */
    @Transactional
    public boolean cambiarReferencia(Long puestoId, String referencia, Long usuarioId) {
        String limpia = (referencia == null || referencia.isBlank()) ? null : referencia.trim();
        return puestoDao.actualizarReferencia(puestoId, limpia, usuarioId) > 0;
    }

    /**
     * Desbloquea una caseta bloqueada (X -> L). Nunca reactiva una anulada: la condicion
     * viaja en el WHERE del UPDATE.
     * @return true si se desbloqueo; false si no estaba bloqueada o estaba anulada.
     */
    @Transactional
    public boolean desbloquear(Long puestoId, Long usuarioId) {
        boolean ok = puestoDao.desbloquearSiBloqueada(puestoId, usuarioId) > 0;
        log.info("Desbloqueo puesto={} usuario={} -> {}", puestoId, usuarioId, ok ? "OK" : "RECHAZADA");
        return ok;
    }

    /** Alta de una caseta suelta dentro de una categoria. @return la caseta creada, o null si la categoria no existe. */
    @Transactional
    public Puesto crear(Long categoriaId, String codigo, String tamano, Long usuarioId) {
        Categoria c = categoriaService.findById(categoriaId);
        if (c == null) return null;

        Date ahora = new Date();
        Puesto p = new Puesto();
        p.setCodigo(codigo);
        p.setTamano(tamano);
        p.setEstadoPuesto(Puesto.LIBRE);
        p.setCategoria(c);
        p.setMapaEscala(1.0);
        // La auditoria de JPA esta apagada: estos campos se ponen a mano o quedan nulos.
        p.setEstado(Puesto.REGISTRO_ACTIVO);
        p.setRegistro(ahora);
        p.setModificacion(ahora);
        p.setRegistroIdUsuario(usuarioId);
        p.setModificacionIdUsuario(usuarioId);
        return puestoDao.save(p);
    }
    // ===== Numeracion de las casetas sobre el plano =====

    /** El numero nuevo de una caseta. Es lo que envia el editor al renumerar un grupo. */
    public record Codigo(Long id, String codigo) {
    }

    /**
     * Resultado de una renumeracion: los ids que de verdad cambiaron (para difundirlos), o
     * el motivo por el que se rechazo el lote entero sin escribir nada.
     */
    public record ResultadoRenumeracion(boolean ok, String mensaje, List<Long> cambiados) {
    }

    /**
     * Renumera un grupo de casetas de una sola vez.
     *
     * Es todo o nada a proposito. Renumerar suele ser una PERMUTACION —la 3 pasa a ser la 5 y
     * la 5 a ser la 3—, asi que no existe un orden de aplicacion caseta por caseta que no pase
     * por un estado con codigos repetidos. Por eso se valida el conjunto RESULTANTE (los codigos
     * del lote mas los de las casetas de esas mismas categorias que no entran en el) y solo
     * despues se escribe. Si algo choca, no se toca ni una fila y el editor recibe un 409.
     *
     * La unicidad se mira POR CATEGORIA, que es como se asignan los codigos hoy: dos casetas de
     * categorias distintas pueden llamarse las dos "3" y eso no es un error.
     */
    @Transactional
    public ResultadoRenumeracion renumerar(List<Codigo> cambios, Long usuarioId) {
        if (cambios == null || cambios.isEmpty()) {
            return new ResultadoRenumeracion(false, "No se indico ninguna caseta", List.of());
        }

        // 1. Normalizar y detectar entradas invalidas antes de tocar la base.
        Map<Long, String> pedidos = new LinkedHashMap<>();
        for (Codigo c : cambios) {
            if (c == null || c.id() == null) {
                return new ResultadoRenumeracion(false, "Llego una caseta sin identificador", List.of());
            }
            String codigo = c.codigo() == null ? "" : c.codigo().trim();
            if (codigo.isEmpty()) {
                return new ResultadoRenumeracion(false, "Hay una caseta sin numero", List.of());
            }
            if (pedidos.put(c.id(), codigo) != null) {
                return new ResultadoRenumeracion(false, "La misma caseta viene dos veces en el lote", List.of());
            }
        }

        // 2. Cargar las casetas del lote. Se extraen los datos AHORA porque los UPDATE de
        //    abajo llevan clearAutomatically y vaciarian el contexto de persistencia.
        List<Puesto> delLote = puestoDao.findAllById(pedidos.keySet());
        if (delLote.size() != pedidos.size()) {
            return new ResultadoRenumeracion(false, "Alguna caseta ya no existe", List.of());
        }
        Map<Long, String> codigoActual = new HashMap<>();
        Map<Long, Long> categoriaDe = new HashMap<>();
        Map<Long, String> nombreCategoria = new HashMap<>();
        for (Puesto p : delLote) {
            if (Puesto.REGISTRO_ANULADO.equals(p.getEstado())) {
                return new ResultadoRenumeracion(false, "No se puede numerar una caseta anulada", List.of());
            }
            Long catId = p.getCategoria() != null ? p.getCategoria().getId() : null;
            codigoActual.put(p.getId(), p.getCodigo());
            categoriaDe.put(p.getId(), catId);
            if (catId != null) nombreCategoria.putIfAbsent(catId, p.getCategoria().getNombre());
        }

        // 3. Validar el conjunto resultante, categoria por categoria.
        for (Long catId : new LinkedHashSet<>(categoriaDe.values())) {
            List<Long> idsAqui = categoriaDe.entrySet().stream()
                    .filter(e -> Objects.equals(e.getValue(), catId))
                    .map(Map.Entry::getKey).toList();

            // Codigos que seguiran ocupados por casetas que NO se renumeran.
            Set<String> ajenos = new HashSet<>();
            if (catId != null) {
                for (Puesto p : puestoDao.activosDeCategoria(catId)) {
                    if (!pedidos.containsKey(p.getId()) && p.getCodigo() != null) {
                        ajenos.add(p.getCodigo().trim());
                    }
                }
            }

            Set<String> enLote = new HashSet<>();
            String etiqueta = catId != null ? "\"" + nombreCategoria.get(catId) + "\"" : "sin categoria";
            for (Long id : idsAqui) {
                String codigo = pedidos.get(id);
                if (!enLote.add(codigo)) {
                    return new ResultadoRenumeracion(false,
                            "El numero " + codigo + " se repite dentro de la seleccion en " + etiqueta,
                            List.of());
                }
                if (ajenos.contains(codigo)) {
                    return new ResultadoRenumeracion(false,
                            "El numero " + codigo + " ya lo tiene otra caseta de " + etiqueta,
                            List.of());
                }
            }
        }

        // 4. Escribir. Solo las que de verdad cambian: renumerar una fila entera suele dejar
        //    la mitad con el numero que ya tenia, y difundirlas seria ruido en todos los mapas.
        List<Long> cambiados = new ArrayList<>();
        for (Map.Entry<Long, String> e : pedidos.entrySet()) {
            String actual = codigoActual.get(e.getKey());
            if (e.getValue().equals(actual == null ? null : actual.trim())) continue;
            if (puestoDao.actualizarCodigo(e.getKey(), e.getValue(), usuarioId) > 0) {
                cambiados.add(e.getKey());
            }
        }
        log.info("Renumeracion: {} casetas cambiadas de {} enviadas (usuario {})",
                cambiados.size(), pedidos.size(), usuarioId);
        return new ResultadoRenumeracion(true,
                "Numeradas " + cambiados.size() + " caseta(s)", cambiados);
    }

    /**
     * Un precio propio pedido para una caseta. {@code precio} nulo = quitarselo y volver al
     * de su categoria, que es una orden tan legitima como ponerlo.
     */
    public record PrecioCaseta(Long id, java.math.BigDecimal precio) {
    }

    /**
     * Pone el precio propio de un grupo de casetas (V37).
     *
     * A diferencia de renumerar, esto NO es todo o nada, y la diferencia es deliberada:
     * renumerar es una permutacion —un numero repetido a mitad de camino deja el plano
     * incoherente— mientras que los precios son independientes entre si. Aqui, si una caseta
     * del lote ya no existe, las demas se guardan igual y se informa de cuantas se tocaron.
     * Rechazar las cuarenta porque una se anulo hace media hora seria peor.
     *
     * Lo que si se valida antes de escribir nada es el propio numero: un precio negativo no
     * significa nada y solo puede venir de un tecleo. Ahi si se rechaza el lote entero, porque
     * es un error de quien lo manda y no una carrera con otro usuario.
     *
     * <b>Se reprecian tambien las casetas vendidas y reservadas, a proposito.</b> El precio de
     * la caseta es el VIGENTE, el de la proxima venta; el de una venta ya hecha esta congelado
     * en {@code inscripcion_puesto.costo} y no lo toca nadie desde aqui. Bloquear la edicion
     * de una caseta vendida no protegeria ese importe —ya esta a salvo— y en cambio impediria
     * corregir el precio de cara a la siguiente edicion de la feria.
     */
    @Transactional
    public ResultadoRenumeracion actualizarPrecios(List<PrecioCaseta> cambios, Long usuarioId) {
        if (cambios == null || cambios.isEmpty()) {
            return new ResultadoRenumeracion(false, "No se indico ninguna caseta", List.of());
        }

        Map<Long, java.math.BigDecimal> pedidos = new LinkedHashMap<>();
        for (PrecioCaseta c : cambios) {
            if (c == null || c.id() == null) {
                return new ResultadoRenumeracion(false, "Llego una caseta sin identificador", List.of());
            }
            if (c.precio() != null && c.precio().signum() < 0) {
                return new ResultadoRenumeracion(false, "Un precio no puede ser negativo", List.of());
            }
            if (pedidos.containsKey(c.id())) {
                return new ResultadoRenumeracion(false, "La misma caseta viene dos veces en el lote", List.of());
            }
            // Se normaliza a 2 decimales, los mismos que guarda la columna: sin esto, mandar
            // 1000.999 se guardaria redondeado y el cliente seguiria mostrando lo que escribio,
            // asi que la pantalla y la base dirian cosas distintas hasta la siguiente recarga.
            pedidos.put(c.id(), c.precio() == null
                    ? null
                    : c.precio().setScale(2, java.math.RoundingMode.HALF_UP));
        }

        List<Long> cambiados = new ArrayList<>();
        for (Map.Entry<Long, java.math.BigDecimal> e : pedidos.entrySet()) {
            if (puestoDao.actualizarPrecio(e.getKey(), e.getValue(), usuarioId) > 0) {
                cambiados.add(e.getKey());
            }
        }

        log.info("Precios propios: {} casetas cambiadas de {} enviadas (usuario {})",
                cambiados.size(), pedidos.size(), usuarioId);
        return new ResultadoRenumeracion(true,
                "Precio guardado en " + cambiados.size() + " caseta(s)", cambiados);
    }
}
