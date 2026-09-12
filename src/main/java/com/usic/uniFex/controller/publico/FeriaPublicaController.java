package com.usic.uniFex.controller.publico;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dao.ICategoriaDao;
import com.usic.uniFex.model.dao.IEdicionDao;
import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.Edicion;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.dto.EdicionDTO;
import com.usic.uniFex.model.dto.NocheFexpoDTO;
import com.usic.uniFex.model.dto.PlanoDTO;
import com.usic.uniFex.model.service.NochesFexpoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/publico/feria")
@RequiredArgsConstructor
public class FeriaPublicaController {

    private final IEdicionDao edicionDao;
    private final ICategoriaDao categoriaDao;
    private final IPuestoDao puestoDao;
    private final com.usic.uniFex.model.service.PlanoService planoService;
    private final NochesFexpoService nochesFexpoService;

    /**
     * Información pública de la feria: edición activa, categorías, estadísticas de casetas y plano.
     * Sin autenticación: accesible desde la web pública, QR, APK sin login, etc.
     */
    @GetMapping
    public Map<String, Object> info() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();

        // 1. Edición activa
        Edicion edicionActiva = edicionDao.findFirstByActivaTrueOrderByAnioDesc().orElse(null);
        if (edicionActiva != null) {
            // LinkedHashMap y no Map.of: "plano" es null en cuanto la edición no tiene un
            // plano propio subido (el caso normal de una edición recién creada), y Map.of
            // lanza NullPointerException ante cualquier valor null, aunque sea uno solo.
            Map<String, Object> edicionInfo = new LinkedHashMap<>();
            edicionInfo.put("id", edicionActiva.getId());
            edicionInfo.put("nombre", edicionActiva.getNombre());
            edicionInfo.put("anio", edicionActiva.getAnio());
            if (edicionActiva.getPlanoArchivo() != null) {
                // Igual que arriba: ancho/alto/subidoEn pueden ser null en datos existentes
                // aunque ya haya un archivo, así que tampoco aquí sirve Map.of.
                Map<String, Object> planoInfo = new LinkedHashMap<>();
                planoInfo.put("archivo", edicionActiva.getPlanoArchivo());
                planoInfo.put("ancho", edicionActiva.getPlanoAncho());
                planoInfo.put("alto", edicionActiva.getPlanoAlto());
                planoInfo.put("version", edicionActiva.getPlanoVersion());
                planoInfo.put("subidoEn", edicionActiva.getPlanoSubidoEn());
                edicionInfo.put("plano", planoInfo);
            } else {
                edicionInfo.put("plano", null);
            }
            edicionInfo.put("urlPlano", edicionActiva.getPlanoArchivo() != null
                    ? "/files/" + edicionActiva.getPlanoArchivo() + "?v=" + edicionActiva.getPlanoVersion()
                    : "/files/mapa.pdf");
            cuerpo.put("edicion", edicionInfo);
        } else {
            cuerpo.put("edicion", null);
        }

        // 2. Todas las ediciones (para selector histórico)
        // LinkedHashMap y no Map.of: `activa` es un Boolean de entidad (puede ser null en
        // ediciones antiguas que no pasaron por el backfill de V6), y Map.of no admite null.
        List<Map<String, Object>> ediciones = edicionDao.findAllByOrderByAnioDesc().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("nombre", e.getNombre());
                    m.put("anio", e.getAnio());
                    m.put("activa", Boolean.TRUE.equals(e.getActiva()));
                    return m;
                })
                .toList();
        cuerpo.put("ediciones", ediciones);

        // 3. Categorías con color, forma y precio base
        List<Categoria> categorias = categoriaDao.findAll();
        List<Map<String, Object>> categoriasInfo = categorias.stream()
                .filter(c -> c.getEstado() == null || !"X".equals(c.getEstado()))
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("nombre", c.getNombre());
                    m.put("descripcion", c.getDescripcion());
                    m.put("color", c.getColor());
                    m.put("forma", c.getForma());
                    m.put("tamanoMapa", c.getTamanoMapa());
                    m.put("precioBase", c.getPrecioBase());
                    return m;
                })
                .toList();
        cuerpo.put("categorias", categoriasInfo);

        // 4. Estadísticas globales de casetas (solo activas)
        List<Puesto> puestosActivos = puestoDao.listarActivos();
        long total = puestosActivos.size();
        long libres = puestosActivos.stream().filter(p -> "L".equals(p.getEstadoPuesto())).count();
        long enTramite = puestosActivos.stream().filter(p -> "T".equals(p.getEstadoPuesto())).count();
        long ocupados = puestosActivos.stream().filter(p -> "O".equals(p.getEstadoPuesto())).count();
        long bloqueados = puestosActivos.stream().filter(p -> "X".equals(p.getEstadoPuesto())).count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("libres", libres);
        stats.put("enTramite", enTramite);
        stats.put("ocupados", ocupados);
        stats.put("bloqueados", bloqueados);
        stats.put("porcentajeOcupacion", total > 0 ? Math.round((double) ocupados / total * 100) : 0);
        cuerpo.put("estadisticas", stats);

        // 5. Detalle por categoría
        Map<Long, List<Puesto>> porCategoria = puestosActivos.stream()
                .filter(p -> p.getCategoria() != null)
                .collect(Collectors.groupingBy(p -> p.getCategoria().getId()));

        List<Map<String, Object>> statsPorCategoria = categorias.stream()
                .filter(c -> c.getEstado() == null || !"X".equals(c.getEstado()))
                .map(c -> {
                    List<Puesto> lista = porCategoria.getOrDefault(c.getId(), List.of());
                    long catTotal = lista.size();
                    long catLibres = lista.stream().filter(p -> "L".equals(p.getEstadoPuesto())).count();
                    long catOcupados = lista.stream().filter(p -> "O".equals(p.getEstadoPuesto())).count();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("categoriaId", c.getId());
                    m.put("categoriaNombre", c.getNombre());
                    m.put("color", c.getColor());
                    m.put("forma", c.getForma());
                    m.put("total", catTotal);
                    m.put("libres", catLibres);
                    m.put("ocupados", catOcupados);
                    m.put("porcentajeOcupacion", catTotal > 0 ? Math.round((double) catOcupados / catTotal * 100) : 0);
                    return m;
                })
                .toList();
        cuerpo.put("estadisticasPorCategoria", statsPorCategoria);

        // 6. Noches de FEXPO (cartelera de artistas, administrable desde el panel — ver V26)
        List<NocheFexpoDTO> noches = nochesFexpoService.listarDeEdicionActiva().stream()
                .map(NocheFexpoDTO::de)
                .toList();
        cuerpo.put("noches", noches);

        // 7. Plano (info completa para el visor público)
        PlanoDTO plano = planoService.activo();
        if (plano != null) {
            cuerpo.put("plano", Map.of(
                    "url", plano.url(),
                    "ancho", plano.ancho(),
                    "alto", plano.alto(),
                    "version", plano.version(),
                    "propio", plano.propio()));
        }

        return cuerpo;
    }

    /**
     * Solo la cartelera de noches. Los cambios llegan en vivo por WebSocket
     * ({@code /topic/publico/noches}, ver NochesFexpoEventPublisher); este GET es para que el
     * cliente se ponga al día cada vez que (re)conecta, porque lo que cambió mientras estaba
     * desconectado no se vuelve a difundir. Aparte del {@code /api/publico/feria} completo,
     * que recalcula las estadísticas de todas las casetas en cada llamada.
     */
    @GetMapping("/noches")
    public List<NocheFexpoDTO> noches() {
        return nochesFexpoService.listarDeEdicionActiva().stream()
                .map(NocheFexpoDTO::de)
                .toList();
    }

    /**
     * Solo estadísticas ligeras (para widgets, contadores en tiempo real, etc.)
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<Puesto> puestosActivos = puestoDao.listarActivos();
        long total = puestosActivos.size();
        long libres = puestosActivos.stream().filter(p -> "L".equals(p.getEstadoPuesto())).count();
        long ocupados = puestosActivos.stream().filter(p -> "O".equals(p.getEstadoPuesto())).count();

        return Map.of(
                "total", total,
                "libres", libres,
                "ocupados", ocupados,
                "enTramite", puestosActivos.stream().filter(p -> "T".equals(p.getEstadoPuesto())).count(),
                "bloqueados", puestosActivos.stream().filter(p -> "X".equals(p.getEstadoPuesto())).count(),
                "porcentajeOcupacion", total > 0 ? Math.round((double) ocupados / total * 100) : 0);
    }
}