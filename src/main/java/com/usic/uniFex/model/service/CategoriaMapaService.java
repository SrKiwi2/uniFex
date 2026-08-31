package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.IService.ICategoriaService;
import com.usic.uniFex.model.IService.IPuestoService;
import com.usic.uniFex.model.dao.ICategoriaDao;
import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.Puesto;

import lombok.RequiredArgsConstructor;

/** Alta, edicion, ajuste de cantidad y baja de categorias desde el diseñador de plano (Fase 3). */
@Service
@RequiredArgsConstructor
public class CategoriaMapaService {

    private final ICategoriaService categoriaService;
    private final IPuestoService puestoService;
    private final IPuestoDao puestoDao;
    private final ICategoriaDao categoriaDao;

    /**
     * @param precioBase precio de venta de cada caseta, en Bs. Null = 0 ("sin definir").
     * @param tamano     medida de negocio de las casetas ("3x3"). Null = {@link Categoria#TAMANO_POR_DEFECTO}.
     */
    public record NuevaCategoria(String nombre, Integer cantidad, String color, String forma,
                                 Double tamanoMapa, BigDecimal precioBase, String tamano) {
    }

    /** Cambios parciales sobre una categoria: los campos nulos no se tocan. */
    public record CambioCategoria(String nombre, String color, String forma, Double tamanoMapa,
                                  BigDecimal precioBase) {
    }

    /** Resultado de ajustar la cantidad: cuantas se crearon o anularon, y cuantas no se pudieron quitar. */
    public record ResultadoAjuste(int creadas, int anuladas, int noQuitadas, List<Long> afectados) {
    }

    /** Crea una categoria y sus N casetas (codigo 1..N, libres). */
    @Transactional
    public Categoria crear(NuevaCategoria req, Long usuarioId) {
        Date now = new Date();
        Categoria c = new Categoria();
        c.setNombre(req.nombre());
        c.setColor(req.color());
        c.setForma(req.forma());
        c.setTamanoMapa(req.tamanoMapa() != null ? req.tamanoMapa() : Categoria.TAMANO_MAPA_POR_DEFECTO);
        // El precio se fija aqui y no en la base: Hibernate manda la columna con NULL en el
        // INSERT, asi que el DEFAULT 0 de precio_base nunca llegaria a aplicarse.
        c.setPrecioBase(req.precioBase() != null ? req.precioBase() : Categoria.PRECIO_POR_DEFECTO);
        // _estado en categoria/puesto vale 'A' o 'X', no "ACTIVO" (eso es de usuario/inscripcion).
        c.setEstado(Categoria.REGISTRO_ACTIVO);
        sellarRegistro(c, now, usuarioId);
        Categoria guardada = categoriaService.save(c);

        String tamano = (req.tamano() != null && !req.tamano().isBlank())
                ? req.tamano().trim() : Categoria.TAMANO_POR_DEFECTO;
        int n = (req.cantidad() == null) ? 0 : Math.max(0, req.cantidad());
        for (int i = 1; i <= n; i++) nuevaCaseta(guardada, String.valueOf(i), tamano, now, usuarioId);
        return guardada;
    }

    /** Edita nombre/color/forma/tamaño/precio de una categoria (solo los campos no nulos). */
    @Transactional
    public Categoria actualizar(Long id, CambioCategoria cambio) {
        Categoria c = categoriaService.findById(id);
        if (c == null) return null;
        if (cambio.nombre() != null) c.setNombre(cambio.nombre());
        if (cambio.color() != null) c.setColor(cambio.color());
        if (cambio.forma() != null) c.setForma(cambio.forma());
        if (cambio.tamanoMapa() != null) c.setTamanoMapa(cambio.tamanoMapa());
        // Cambiar el precio NO altera las ventas ya hechas: el costo se copia a
        // inscripcion_puesto en el momento de vender, precisamente para eso.
        if (cambio.precioBase() != null) c.setPrecioBase(cambio.precioBase());
        c.setModificacion(new Date());
        return categoriaService.save(c);
    }

    /**
     * Lleva la categoria a exactamente {@code cantidadDeseada} casetas activas, si se puede.
     *
     * Subir crea casetas nuevas con el siguiente codigo. Bajar anula las de codigo mas alto,
     * pero solo las que estan libres y sin ventas (via {@link IPuestoDao#anularSiLibreYSinVentas}):
     * una caseta vendida o reservada nunca se sacrifica por reducir un numero, y se informa
     * cuantas no se pudieron quitar.
     *
     * @return el detalle del ajuste, o null si la categoria no existe.
     */
    @Transactional
    public ResultadoAjuste ajustarCantidad(Long categoriaId, int cantidadDeseada, Long usuarioId) {
        Categoria c = categoriaService.findById(categoriaId);
        if (c == null) return null;

        List<Puesto> activas = puestoDao.activosDeCategoria(categoriaId);
        int actual = activas.size();
        List<Long> afectados = new ArrayList<>();

        if (cantidadDeseada > actual) {
            Date now = new Date();
            int siguiente = maxCodigoNumerico(activas) + 1;
            int aCrear = cantidadDeseada - actual;
            // Las casetas nuevas heredan la medida de sus hermanas: si la categoria ya es de
            // 3x3, ampliar la cantidad no debe colar casetas de otro tamaño.
            String tamano = activas.stream()
                    .map(Puesto::getTamano)
                    .filter(t -> t != null && !t.isBlank())
                    .findFirst()
                    .orElse(Categoria.TAMANO_POR_DEFECTO);
            for (int i = 0; i < aCrear; i++) {
                Puesto p = nuevaCaseta(c, String.valueOf(siguiente + i), tamano, now, usuarioId);
                afectados.add(p.getId());
            }
            return new ResultadoAjuste(aCrear, 0, 0, afectados);
        }

        if (cantidadDeseada < actual) {
            // Anular desde el codigo mas alto hacia abajo, saltando lo que no se pueda.
            activas.sort(Comparator.comparingInt(CategoriaMapaService::codigoNumerico).reversed());
            int aQuitar = actual - cantidadDeseada;
            int anuladas = 0, noQuitadas = 0;
            for (Puesto p : activas) {
                if (anuladas >= aQuitar) break;
                if (puestoDao.anularSiLibreYSinVentas(p.getId(), usuarioId) > 0) {
                    anuladas++;
                    afectados.add(p.getId());
                } else {
                    noQuitadas++;
                }
            }
            return new ResultadoAjuste(0, anuladas, noQuitadas, afectados);
        }

        return new ResultadoAjuste(0, 0, 0, afectados); // ya estaba en la cantidad deseada
    }

    /**
     * Da de baja la categoria entera. Solo procede si NINGUNA de sus casetas tiene ventas ni
     * esta ocupada/reservada; si alguna la tiene, no se toca nada y se devuelve false, porque
     * borrar media categoria dejaria un estado peor que no borrar.
     *
     * @return los ids de las casetas anuladas (para difundirlas), o null si no se pudo eliminar
     *         (con o sin la categoria existiendo — el controlador distingue por el mensaje).
     */
    @Transactional
    public List<Long> eliminar(Long categoriaId, Long usuarioId) {
        Categoria c = categoriaService.findById(categoriaId);
        if (c == null) return null;
        if (puestoDao.contarNoEliminablesDeCategoria(categoriaId) > 0) return null;

        List<Long> ids = puestoDao.idsActivosDeCategoria(categoriaId);
        puestoDao.anularCasetasDeCategoria(categoriaId, usuarioId);
        categoriaDao.anular(categoriaId);
        return ids;
    }

    // ===== helpers =====

    private Puesto nuevaCaseta(Categoria c, String codigo, String tamano, Date now, Long usuarioId) {
        Puesto p = new Puesto();
        p.setCodigo(codigo);
        // El tamaño no puede quedar NULL: es la medida que se le enseña al cliente y ademas
        // lo consulta el calculo de precio de respaldo de la stored function.
        p.setTamano(tamano != null ? tamano : Categoria.TAMANO_POR_DEFECTO);
        p.setEstadoPuesto(Puesto.LIBRE);
        p.setCategoria(c);
        p.setMapaEscala(1.0);
        p.setEstado(Puesto.REGISTRO_ACTIVO);
        sellarRegistro(p, now, usuarioId);
        return puestoService.save(p);
    }

    /** La auditoria de JPA esta apagada: estos campos se ponen a mano o quedan nulos. */
    private void sellarRegistro(com.usic.uniFex.Config.AuditoriaConfig e, Date now, Long usuarioId) {
        e.setRegistro(now);
        e.setModificacion(now);
        e.setRegistroIdUsuario(usuarioId);
        e.setModificacionIdUsuario(usuarioId);
    }

    private static int codigoNumerico(Puesto p) {
        try {
            return Integer.parseInt(p.getCodigo().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static int maxCodigoNumerico(List<Puesto> casetas) {
        return casetas.stream().mapToInt(CategoriaMapaService::codigoNumerico).max().orElse(0);
    }
}
