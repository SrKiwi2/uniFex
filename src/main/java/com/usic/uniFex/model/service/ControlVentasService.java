package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dto.AnalisisDTO;
import com.usic.uniFex.model.dto.ControlVentasDTO;
import com.usic.uniFex.model.dto.ControlVentasDTO.Categoria;
import com.usic.uniFex.model.dto.ControlVentasDTO.Dia;
import com.usic.uniFex.model.dto.ControlVentasDTO.Puesto;
import com.usic.uniFex.model.dto.ControlVentasDTO.Responsable;
import com.usic.uniFex.model.dto.ControlVentasDTO.Resumen;
import com.usic.uniFex.model.dto.ControlVentasDTO.Venta;
import com.usic.uniFex.model.dto.ControlVentasDTO.Vendedor;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

/**
 * Control de ventas: la rendicion de cuentas de la feria. Cada operacion con todas sus celdas,
 * y lo vendido, recaudado y por rendir de cada vendedor y cada categoria.
 *
 * <h2>Todo se deriva; no hay tablas propias</h2>
 * Se carga la edicion entera con tres consultas planas (ventas, casetas, responsables con su
 * credencial) mas el inventario de {@link AnalisisVentasService#ocupacion()}, y el resto se
 * calcula aqui. Son unos cientos de filas: traerlas cuesta milisegundos, y a cambio el tablero,
 * la tabla, el Excel y el PDF leen exactamente las mismas cifras.
 *
 * <h2>Rendido = comprobante adjunto</h2>
 * Una venta esta rendida si tiene su comprobante adjunto ({@code inscripcion.img_comprobante}):
 * la foto del deposito, el voucher o el recibo. <b>Banco y numero no se exigen</b> — muchos
 * vendedores solo subieron la imagen, y la imagen es el respaldo. Es la misma regla que ya
 * aplican Credenciales, Mis ventas y el tablero de direccion; un libro de pagos aparte haria
 * que esta pantalla dijera "pagado" de una venta que el resto del sistema ve sin comprobante.
 * Las credenciales extra, igual: cada una con su comprobante ({@code comprobante_extra}).
 *
 * "Al contado" sin comprobante NO cuenta como rendido: marcar contado dice COMO se pago, no que
 * el respaldo exista.
 *
 * <h2>Las mismas dos reglas que el resto de reportes</h2>
 * Solo la edicion activa, y nada anulado ({@code _estado = 'X'}): ni ventas, ni casetas de la
 * venta, ni responsables.
 */
@Service
@RequiredArgsConstructor
public class ControlVentasService {

    /** Una carpa por caseta adquirida. */
    public static final int CARPAS_POR_PUESTO = 1;
    /** Dos credenciales incluidas por caseta: las de sus dos responsables. */
    public static final int CREDENCIALES_POR_PUESTO = RegistroVentaService.RESPONSABLES_POR_CASETA;

    private static final String EDICION_ACTIVA =
            "(select ed.id from edicion ed where ed.activa limit 1)";

    /** Venta viva de la edicion activa; el alias de inscripcion es siempre {@code i}. */
    private static final String VENTA_VIVA = """
            (i."_estado" is null or i."_estado" <> 'X')
               and i.id_edicion = """ + EDICION_ACTIVA;

    /** Nombre legible de un usuario: su persona, o el login si no tiene. */
    private static String nombreUsuario(String u, String p) {
        return "coalesce(nullif(btrim(concat_ws(' ', " + p + ".nombre, " + p + ".paterno, " + p
                + ".materno)), ''), " + u + ".username)";
    }

    @PersistenceContext
    private EntityManager em;

    private final AnalisisVentasService analisis;

    /**
     * Filtros de la pantalla. Se aplican sobre las ventas ya calculadas: el monto por rendir o
     * el codigo de expositor no existen en la base.
     */
    public record Filtros(Long vendedorId, Long categoriaId, LocalDate desde, LocalDate hasta,
                          String estadoPago, String texto) {

        public static Filtros ninguno() {
            return new Filtros(null, null, null, null, null, null);
        }
    }

    /** La edicion calculada. Cada mapa va por id de inscripcion. */
    public record Calculo(List<Venta> ventas, Map<Long, List<Puesto>> puestos,
                          Map<Long, List<Responsable>> responsables,
                          Map<Long, Set<Long>> categoriasDeVenta) {

        public List<Puesto> puestosDe(Long id) {
            return puestos.getOrDefault(id, List.of());
        }

        public List<Responsable> responsablesDe(Long id) {
            return responsables.getOrDefault(id, List.of());
        }
    }

    // ================================================================== consultas publicas

    @Transactional(readOnly = true)
    public Calculo calcular() {
        return cargar();
    }

    /** Las ventas que pasan el filtro, mas recientes primero. */
    @Transactional(readOnly = true)
    public List<Venta> ventas(Filtros f) {
        return filtrar(cargar(), f);
    }

    /**
     * Todo lo de una venta. Vacio si no existe, esta anulada o es de otra edicion.
     *
     * Se carga la edicion entera y no solo la venta: "compras del expositor" cuenta las OTRAS
     * ventas con su mismo codigo, y eso no se sabe mirando una sola.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<ControlVentasDTO.Ficha> ficha(Long inscripcionId) {
        Calculo c = cargar();
        return c.ventas().stream()
                .filter(v -> v.getInscripcionId().equals(inscripcionId))
                .findFirst()
                .map(v -> new ControlVentasDTO.Ficha(v, c.puestosDe(inscripcionId),
                        c.responsablesDe(inscripcionId)));
    }

    @Transactional(readOnly = true)
    public ControlVentasDTO.Tablero tablero(Filtros f) {
        Calculo c = cargar();
        List<Venta> ventas = filtrar(c, f);
        return new ControlVentasDTO.Tablero(resumen(ventas), vendedores(ventas),
                categorias(c, ventas), avance(ventas));
    }

    /** Opciones de los desplegables de filtro: solo lo que aparece en la edicion activa. */
    @Transactional(readOnly = true)
    public Map<String, Object> opcionesDeFiltro() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("vendedores", opciones("""
                select distinct u.id, %s as nombre
                  from inscripcion i
                  join usuario u on u.id = i."_registro_id_usuario"
                  left join persona pe on pe.id = u.persona_id
                 where %s
                 order by nombre
                """.formatted(nombreUsuario("u", "pe"), VENTA_VIVA)));
        r.put("categorias", opciones("""
                select c.id, c.nombre from categoria c
                 where c."_estado" is null or c."_estado" <> 'X'
                 order by c.nombre
                """));
        return r;
    }

    // ================================================================== agregados

    /** La rendicion de cuentas de cada vendedor, de mas vendido a menos. */
    public List<Vendedor> vendedores(List<Venta> ventas) {
        BigDecimal totalGeneral = suma(ventas, Venta::getTotalVendido);

        Map<Long, List<Venta>> porVendedor = new LinkedHashMap<>();
        for (Venta v : ventas) {
            porVendedor.computeIfAbsent(v.getVendedorId() == null ? -1L : v.getVendedorId(),
                    k -> new ArrayList<>()).add(v);
        }

        List<Vendedor> r = new ArrayList<>();
        for (List<Venta> suyas : porVendedor.values()) {
            Venta una = suyas.get(0);
            BigDecimal total = suma(suyas, Venta::getTotalVendido);
            r.add(new Vendedor(una.getVendedorId(),
                    una.getVendedor() == null ? "(sin usuario)" : una.getVendedor(),
                    una.getVendedorUsuario(), suyas.size(),
                    (int) suyas.stream().map(Venta::getCodigoExpositor).distinct().count(),
                    suyas.stream().mapToInt(Venta::getCantidadPuestos).sum(),
                    suyas.stream().mapToInt(Venta::getExtras).sum(),
                    suma(suyas, Venta::getImportePuestos), suma(suyas, Venta::getImporteExtras),
                    total, suma(suyas, Venta::getRecaudadoPuestos), suma(suyas, Venta::getRecaudadoExtras),
                    suma(suyas, Venta::getTotalRecaudado), suma(suyas, Venta::getPorRendir),
                    (int) suyas.stream().filter(v -> v.getPorRendir().signum() > 0).count(),
                    porcentaje(total, totalGeneral),
                    suyas.stream().mapToInt(Venta::getCredencialesTotal).sum(),
                    suyas.stream().mapToInt(Venta::getCredencialesEmitidas).sum()));
        }
        r.sort(Comparator.comparing(Vendedor::totalVendido).reversed()
                .thenComparing(Vendedor::vendedor, Comparator.nullsLast(Comparator.naturalOrder())));
        return r;
    }

    /**
     * Ventas de casetas por categoria, con las categorias SIN ventas incluidas: una categoria
     * que no vendio nada es la que hay que mirar, y esconderla la vuelve invisible.
     *
     * El inventario (casetas vendibles) sale de {@link AnalisisVentasService#ocupacion()} y no
     * de una consulta propia: es el mismo dato que el tablero de direccion.
     *
     * Lo RECAUDADO de una venta se reparte entre sus casetas en proporcion a su precio.
     */
    public List<Categoria> categorias(Calculo c, List<Venta> ventas) {
        Map<String, int[]> conteo = new LinkedHashMap<>();        // [vendidas, exentas]
        Map<String, BigDecimal> vendido = new HashMap<>();
        Map<String, BigDecimal> recaudado = new HashMap<>();
        for (Venta v : ventas) {
            BigDecimal factor = v.getImportePuestos().signum() == 0 ? BigDecimal.ZERO
                    : v.getRecaudadoPuestos().min(v.getImportePuestos())
                            .divide(v.getImportePuestos(), 10, RoundingMode.HALF_UP);
            for (Puesto p : c.puestosDe(v.getInscripcionId())) {
                String cat = p.categoria() == null ? "(sin categoría)" : p.categoria();
                int[] n = conteo.computeIfAbsent(cat, k -> new int[2]);
                n[0]++;
                if (p.exento()) n[1]++;
                vendido.merge(cat, p.precio(), BigDecimal::add);
                recaudado.merge(cat, p.precio().multiply(factor), BigDecimal::add);
            }
        }

        Map<String, Integer> vendibles = new HashMap<>();
        for (AnalisisDTO.Ocupacion o : analisis.ocupacion()) {
            if (o.categoria() == null) continue;
            vendibles.put(o.categoria(), Math.max(0, o.total() - o.bloqueadas()));
            conteo.computeIfAbsent(o.categoria(), k -> new int[2]);
        }

        int totalVendidas = conteo.values().stream().mapToInt(n -> n[0]).sum();
        BigDecimal totalBs = vendido.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Categoria> r = new ArrayList<>();
        for (Map.Entry<String, int[]> e : conteo.entrySet()) {
            String cat = e.getKey();
            int vendidas = e.getValue()[0];
            int total = vendibles.getOrDefault(cat, vendidas);
            BigDecimal bs = vendido.getOrDefault(cat, BigDecimal.ZERO);
            r.add(new Categoria(cat, vendidas, total,
                    porcentaje(BigDecimal.valueOf(vendidas), BigDecimal.valueOf(totalVendidas)),
                    porcentaje(BigDecimal.valueOf(vendidas), BigDecimal.valueOf(total)),
                    bs.setScale(2, RoundingMode.HALF_UP), porcentaje(bs, totalBs),
                    recaudado.getOrDefault(cat, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                    e.getValue()[1]));
        }
        r.sort(Comparator.comparing(Categoria::vendidoBs).reversed()
                .thenComparing(Categoria::puestosVendidos, Comparator.reverseOrder())
                .thenComparing(Categoria::categoria));
        return r;
    }

    /** Lo vendido cada dia, con el acumulado. Solo los dias con ventas. */
    public List<Dia> avance(List<Venta> ventas) {
        TreeMap<LocalDate, List<Venta>> porDia = new TreeMap<>();
        for (Venta v : ventas) {
            if (v.getFecha() == null) continue;
            porDia.computeIfAbsent(v.getFecha().toLocalDate(), k -> new ArrayList<>()).add(v);
        }
        List<Dia> r = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, List<Venta>> e : porDia.entrySet()) {
            BigDecimal bs = suma(e.getValue(), Venta::getTotalVendido);
            acumulado = acumulado.add(bs);
            r.add(new Dia(e.getKey(), e.getValue().size(),
                    e.getValue().stream().mapToInt(Venta::getCantidadPuestos).sum(), bs, acumulado));
        }
        return r;
    }

    public Resumen resumen(List<Venta> ventas) {
        return new Resumen(
                ventas.size(),
                (int) ventas.stream().map(Venta::getCodigoExpositor).distinct().count(),
                (int) ventas.stream().map(Venta::getVendedorId).filter(Objects::nonNull).distinct().count(),
                ventas.stream().mapToInt(Venta::getCantidadPuestos).sum(),
                ventas.stream().mapToInt(Venta::getPuestosExentos).sum(),
                ventas.stream().mapToInt(Venta::getExtras).sum(),
                suma(ventas, Venta::getImportePuestos), suma(ventas, Venta::getImporteExtras),
                suma(ventas, Venta::getTotalVendido), suma(ventas, Venta::getRecaudadoPuestos),
                suma(ventas, Venta::getRecaudadoExtras), suma(ventas, Venta::getTotalRecaudado),
                suma(ventas, Venta::getPorRendir),
                contar(ventas, ControlVentasDTO.PAGADO), contar(ventas, ControlVentasDTO.PARCIAL),
                contar(ventas, ControlVentasDTO.PENDIENTE), contar(ventas, ControlVentasDTO.SIN_COSTO),
                ventas.stream().mapToInt(Venta::getCarpasCorresponden).sum(),
                ventas.stream().mapToInt(Venta::getCredencialesIncluidas).sum(),
                ventas.stream().mapToInt(Venta::getExtras).sum(),
                ventas.stream().mapToInt(Venta::getCredencialesTotal).sum(),
                ventas.stream().mapToInt(Venta::getResponsablesRegistrados).sum(),
                ventas.stream().mapToInt(Venta::getCredencialesEmitidas).sum());
    }

    // ================================================================== filtro

    public List<Venta> filtrar(Calculo c, Filtros f) {
        if (f == null) return c.ventas();
        String texto = normal(f.texto());
        return c.ventas().stream()
                .filter(v -> f.vendedorId() == null || f.vendedorId().equals(v.getVendedorId()))
                .filter(v -> f.categoriaId() == null
                        || c.categoriasDeVenta().getOrDefault(v.getInscripcionId(), Set.of())
                                .contains(f.categoriaId()))
                .filter(v -> f.desde() == null
                        || (v.getFecha() != null && !v.getFecha().toLocalDate().isBefore(f.desde())))
                .filter(v -> f.hasta() == null
                        || (v.getFecha() != null && !v.getFecha().toLocalDate().isAfter(f.hasta())))
                .filter(v -> vacio(f.estadoPago()) || f.estadoPago().equalsIgnoreCase(v.getEstadoPago()))
                .filter(v -> texto == null || buscable(v).contains(texto))
                .toList();
    }

    /** Todo lo que se puede buscar de una venta, en minusculas y sin tildes. */
    private static String buscable(Venta v) {
        return normal(String.join(" | ", noNulos(v.getCodigoVenta(), v.getCodigoExpositor(),
                v.getEntidad(), v.getRubro(), v.getNit(), v.getRepresentante(), v.getCiRepresentante(),
                v.getCelularRepresentante(), v.getTitular(), v.getCiTitular(), v.getCelularTitular(),
                v.getResponsables(), v.getNombresExtras(), v.getPuestos(), v.getCategorias(),
                v.getSubcategorias(), v.getVendedor(), v.getNumComprobante(),
                String.valueOf(v.getInscripcionId()))));
    }

    // ================================================================== carga y calculo

    private Calculo cargar() {
        // ---- 1. las ventas
        List<Object[]> filasVenta = lista("""
                select i.id, i.nota_codigo, i.fecha_compra, i.inscripcion_estado,
                       u.id, %s, u.username,
                       e.id, e.nombre, e.descripcion, t.nombre, e.nit, e.representante_legal,
                       e.ci_representante, e.celular_representante,
                       i.pago_contado, i.entidad_bancaria, i.num_comprobante, i.img_comprobante
                  from inscripcion i
                  join entidad e on e.id = i.id_entidad
                  left join tipo_entidad t on t.id = e.id_tipo_entidad
                  left join usuario u on u.id = i."_registro_id_usuario"
                  left join persona vp on vp.id = u.persona_id
                 where %s
                 order by i.fecha_compra desc nulls last, i.id desc
                """.formatted(nombreUsuario("u", "vp"), VENTA_VIVA));

        // ---- 2. sus casetas
        //
        // El precio de referencia se resuelve con el MISMO orden que al vender
        // (RegistroVentaService.congelarCosto): la opcion elegida; si no la hay y la caseta
        // tiene precio propio, ese; si no, la opcion predeterminada de la categoria.
        List<Object[]> filasPuesto = lista("""
                select ip.id_inscripcion, p.id, p.codigo, c.id, c.nombre,
                       o.nombre, o.precio, p.precio, od.nombre, od.precio, c.precio_base,
                       coalesce(ip.costo, 0)
                  from inscripcion_puesto ip
                  join inscripcion i on i.id = ip.id_inscripcion
                  join puesto p on p.id = ip.id_puesto
                  left join categoria c on c.id = p.id_categoria
                  left join categoria_opcion o on o.id = ip.id_categoria_opcion
                  left join lateral (
                        select x.nombre, x.precio from categoria_opcion x
                         where x.id_categoria = c.id and x.predeterminada
                           and (x."_estado" is null or x."_estado" <> 'X')
                         order by x.id limit 1) od on true
                 where ip.id_puesto is not null
                   and (ip."_estado" is null or ip."_estado" <> 'X')
                   and %s
                 order by ip.id_inscripcion, p.codigo
                """.formatted(VENTA_VIVA));

        // ---- 3. sus responsables, con la PRIMERA vez que se genero su credencial
        //
        // credencial_impresion guarda cada credencial generada: la virtual (la que se mandaba
        // por WhatsApp) y la impresa. Que el envio llegara no quedo registrado; esto es lo mas
        // cerca de "entregada" que hay en la base.
        List<Object[]> filasResp = lista("""
                select i.id, r.id, r.es_titular, r.es_extra, r.monto_extra, r.comprobante_extra,
                       r."_fecha_registro",
                       btrim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)), pe.ci, pe.celular,
                       em.primera, em.veces, %s
                  from inscripcion i
                  join responsable r on r.id_entidad = i.id_entidad
                       and (r."_estado" is null or r."_estado" <> 'X')
                  left join persona pe on pe.id = r.id_persona
                  left join lateral (
                        select ci.impreso_en as primera, ci.impreso_por as por,
                               count(*) over () as veces
                          from credencial_impresion ci
                         where ci.id_responsable = r.id
                         order by ci.impreso_en
                         limit 1) em on true
                  left join usuario ue on ue.id = em.por
                  left join persona pue on pue.id = ue.persona_id
                 where %s
                 order by i.id, r.es_titular desc, r.es_extra, r.id
                """.formatted(nombreUsuario("ue", "pue"), VENTA_VIVA));

        // ---- armado
        Map<Long, List<Puesto>> puestos = new LinkedHashMap<>();
        Map<Long, Set<Long>> categoriasDeVenta = new HashMap<>();
        Map<Long, List<Responsable>> responsables = new LinkedHashMap<>();
        Map<Long, List<Object[]>> puestosCrudos = agrupar(filasPuesto);
        Map<Long, List<Object[]>> respCrudos = agrupar(filasResp);

        List<Venta> ventas = new ArrayList<>();
        for (Object[] f : filasVenta) {
            Venta v = new Venta();
            Long id = numero(f[0]);
            v.setInscripcionId(id);
            v.setCodigoVenta(texto(f[1]) != null ? texto(f[1]) : "V-" + id);
            v.setFecha(momento(f[2]));
            v.setEstadoInscripcion(texto(f[3]));
            v.setVendedorId(numero(f[4]));
            v.setVendedor(texto(f[5]));
            v.setVendedorUsuario(texto(f[6]));
            v.setEntidadId(numero(f[7]));
            v.setEntidad(texto(f[8]));
            v.setRubro(texto(f[9]));
            v.setTipoEntidad(texto(f[10]));
            v.setNit(texto(f[11]));
            v.setRepresentante(texto(f[12]));
            v.setCiRepresentante(texto(f[13]));
            v.setCelularRepresentante(texto(f[14]));
            String[] codigo = codigoExpositor(v.getEntidadId(), v.getNit(), v.getCiRepresentante());
            v.setCodigoExpositor(codigo[0]);
            v.setBaseCodigo(codigo[1]);
            boolean contado = Boolean.TRUE.equals(f[15]);
            String img = texto(f[18]);
            v.setEntidadBancaria(texto(f[16]));
            v.setNumComprobante(texto(f[17]));
            v.setConComprobante(img != null);
            v.setComprobanteUrl(img == null ? null : "/files/" + img);
            v.setFormaPago(contado ? "Al contado" : img != null ? "Depósito / transferencia" : null);

            // -- casetas
            List<Puesto> ps = new ArrayList<>();
            Set<Long> cats = new HashSet<>();
            for (Object[] p : puestosCrudos.getOrDefault(id, List.of())) {
                ps.add(puesto(v, p));
                if (p[3] != null) cats.add(numero(p[3]));
            }
            puestos.put(id, ps);
            categoriasDeVenta.put(id, cats);
            llenarCasetas(v, ps);

            // -- responsables, extras y credenciales
            List<Responsable> rs = new ArrayList<>();
            BigDecimal importeExtras = BigDecimal.ZERO;
            BigDecimal recaudadoExtras = BigDecimal.ZERO;
            int extras = 0;
            int extrasSinComprobante = 0;
            int emitidas = 0;
            LocalDateTime primeraEmision = null;
            List<String> normales = new ArrayList<>();
            List<String> nombresExtra = new ArrayList<>();
            for (Object[] r : respCrudos.getOrDefault(id, List.of())) {
                boolean titular = Boolean.TRUE.equals(r[2]);
                boolean extra = Boolean.TRUE.equals(r[3]);
                BigDecimal monto = extra ? decimal(r[4]) : null;
                String comp = texto(r[5]);
                String nombre = texto(r[7]);
                String ci = texto(r[8]);
                String cel = texto(r[9]);
                LocalDateTime emitida = momento(r[10]);
                rs.add(new Responsable(id, v.getCodigoVenta(), v.getEntidad(), v.getVendedor(), numero(r[1]),
                        titular ? "TITULAR" : extra ? "EXTRA" : "ACOMPAÑANTE", nombre, ci, cel, monto,
                        comp != null, comp == null ? null : "/files/" + comp, momento(r[6]),
                        emitida != null, emitida, emitida == null ? null : texto(r[12]), entero(r[11])));
                if (emitida != null) {
                    emitidas++;
                    if (primeraEmision == null || emitida.isBefore(primeraEmision)) primeraEmision = emitida;
                }
                if (titular && v.getTitular() == null) {
                    v.setTitular(nombre);
                    v.setCiTitular(ci);
                    v.setCelularTitular(cel);
                }
                if (extra) {
                    extras++;
                    importeExtras = importeExtras.add(monto);
                    if (comp != null) recaudadoExtras = recaudadoExtras.add(monto);
                    else if (monto.signum() > 0) extrasSinComprobante++;
                    nombresExtra.add(contacto(nombre, ci, cel));
                } else {
                    normales.add(contacto(nombre, ci, cel));
                }
            }
            responsables.put(id, rs);
            v.setResponsables(String.join("; ", normales));
            v.setNombresExtras(String.join("; ", nombresExtra));
            v.setExtras(extras);
            v.setExtrasSinComprobante(extrasSinComprobante);
            v.setImporteExtras(importeExtras);
            v.setPrecioExtra(extras == 0 ? RegistroVentaService.COSTO_RESPONSABLE_EXTRA
                    : importeExtras.divide(BigDecimal.valueOf(extras), 2, RoundingMode.HALF_UP));

            // -- dinero: rendido = con comprobante adjunto
            v.setTotalVendido(v.getImportePuestos().add(importeExtras));
            v.setRecaudadoPuestos(img != null ? v.getImportePuestos() : BigDecimal.ZERO);
            v.setRecaudadoExtras(recaudadoExtras);
            v.setTotalRecaudado(v.getRecaudadoPuestos().add(recaudadoExtras));
            v.setPorRendir(v.getTotalVendido().subtract(v.getTotalRecaudado()));
            v.setEstadoPago(v.getTotalVendido().signum() == 0 ? ControlVentasDTO.SIN_COSTO
                    : v.getPorRendir().signum() <= 0 ? ControlVentasDTO.PAGADO
                    : v.getTotalRecaudado().signum() > 0 ? ControlVentasDTO.PARCIAL
                    : ControlVentasDTO.PENDIENTE);

            // -- carpas y credenciales
            v.setCarpasCorresponden(v.getCantidadPuestos() * CARPAS_POR_PUESTO);
            v.setCredencialesIncluidas(v.getCantidadPuestos() * CREDENCIALES_POR_PUESTO);
            v.setCredencialesTotal(v.getCredencialesIncluidas() + extras);
            v.setResponsablesRegistrados(rs.size());
            v.setCredencialesEmitidas(emitidas);
            v.setFechaEmision(primeraEmision == null ? null : primeraEmision.toLocalDate());

            ventas.add(v);
        }

        // "Compras del expositor": cuantas ventas comparten su codigo.
        Map<String, Long> compras = ventas.stream()
                .collect(Collectors.groupingBy(Venta::getCodigoExpositor, Collectors.counting()));
        for (Venta v : ventas) v.setComprasExpositor(compras.get(v.getCodigoExpositor()).intValue());

        return new Calculo(ventas, puestos, responsables, categoriasDeVenta);
    }

    private Puesto puesto(Venta v, Object[] p) {
        boolean conOpcion = p[5] != null || p[6] != null;
        BigDecimal propio = p[7] == null ? null : decimal(p[7]);
        String sub;
        BigDecimal lista;
        if (conOpcion) {
            sub = texto(p[5]);
            lista = decimal(p[6]);
        } else if (propio != null) {
            sub = "Precio propio de la caseta";
            lista = propio;
        } else {
            sub = texto(p[8]) != null ? texto(p[8]) : texto(p[4]);
            lista = p[9] != null ? decimal(p[9]) : decimal(p[10]);
        }
        BigDecimal costo = decimal(p[11]);
        boolean exento = costo.signum() == 0;
        BigDecimal descuento = lista.subtract(costo).max(BigDecimal.ZERO);
        return new Puesto(v.getInscripcionId(), v.getCodigoVenta(), v.getFecha(), v.getVendedor(),
                v.getCodigoExpositor(), v.getEntidad(), numero(p[1]), texto(p[2]), texto(p[4]), sub,
                lista, costo, descuento, exento, exento ? sub : null);
    }

    /** Los campos de la venta que salen de sus casetas. */
    private static void llenarCasetas(Venta v, List<Puesto> ps) {
        v.setCantidadPuestos(ps.size());
        v.setPuestos(unir(ps, Puesto::codigo));
        v.setCategorias(unirDistintos(ps, Puesto::categoria));
        v.setSubcategorias(unirDistintos(ps, Puesto::subcategoria));
        // Sin columna de ubicacion en las casetas (referencia esta vacia en toda la base), la
        // ubicacion util es la zona: categoria y codigo.
        v.setUbicacion(ps.stream()
                .map(p -> (p.categoria() == null ? "" : p.categoria() + " · ") + p.codigo())
                .collect(Collectors.joining("; ")));
        Set<BigDecimal> precios = ps.stream().map(p -> p.precio().setScale(2, RoundingMode.HALF_UP))
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        v.setPrecioUnitario(precios.size() == 1 ? precios.iterator().next() : null);
        v.setPreciosUnitarios(precios.stream().map(ControlVentasService::bs).collect(Collectors.joining(" / ")));
        v.setPrecioLista(sumar(ps, Puesto::precioLista));
        v.setDescuento(sumar(ps, Puesto::descuento));
        v.setImportePuestos(sumar(ps, Puesto::precio));
        List<Puesto> exentos = ps.stream().filter(Puesto::exento).toList();
        v.setPuestosExentos(exentos.size());
        v.setMotivoExencion(exentos.isEmpty() ? null : "Exento: " + unirDistintos(exentos, Puesto::motivoExencion));
    }

    // ================================================================== codigo de expositor

    private static final Pattern DIGITOS = Pattern.compile("\\d{5,}");

    /**
     * Un codigo estable para reconocer al mismo negocio en varias compras.
     *
     * Cada venta crea su propia ficha de entidad, asi que el id de la entidad NO sirve: el
     * mismo expositor que compra dos veces tiene dos. Se usa el numero de su NIT y, si no es
     * utilizable, el del C.I. del representante — el mismo prefijo para los dos, porque muchos
     * expositores pequeños ponen su C.I. como NIT y asi se unen igual.
     *
     * "Utilizable" = al menos cinco digitos seguidos y no todos iguales: en la base hay NIT
     * como "00000", "11111111", "S/N" o "NO TIENE", y unirlos juntaria a decenas de negocios
     * distintos en un solo expositor. Del C.I. se toma el primer grupo de digitos, para que
     * "1234567 LP" y "1234567" sean la misma persona.
     */
    static String[] codigoExpositor(Long entidadId, String nit, String ci) {
        String n = digitosUtiles(nit);
        if (n != null) return new String[] { "EXP-" + n, "NIT" };
        String c = digitosUtiles(ci);
        if (c != null) return new String[] { "EXP-" + c, "CI" };
        return new String[] { "ENT-" + entidadId, "FICHA" };
    }

    private static String digitosUtiles(String v) {
        if (v == null) return null;
        Matcher m = DIGITOS.matcher(v);
        if (!m.find()) return null;
        String d = m.group();
        return d.chars().distinct().count() == 1 ? null : d;
    }

    // ================================================================== utilidades

    private List<Object[]> lista(String sql) {
        @SuppressWarnings("unchecked")
        List<Object[]> r = em.createNativeQuery(sql).getResultList();
        return r;
    }

    private List<ControlVentasDTO.Opcion> opciones(String sql) {
        return lista(sql).stream()
                .map(f -> new ControlVentasDTO.Opcion(numero(f[0]), texto(f[1]) == null ? "(sin nombre)" : texto(f[1])))
                .toList();
    }

    private static Map<Long, List<Object[]>> agrupar(List<Object[]> filas) {
        Map<Long, List<Object[]>> r = new HashMap<>();
        for (Object[] f : filas) r.computeIfAbsent(numero(f[0]), k -> new ArrayList<>()).add(f);
        return r;
    }

    private static String contacto(String nombre, String ci, String cel) {
        List<String> extra = new ArrayList<>();
        if (ci != null) extra.add("C.I. " + ci);
        if (cel != null) extra.add("cel. " + cel);
        String n = nombre == null ? "(sin nombre)" : nombre;
        return extra.isEmpty() ? n : n + " (" + String.join(" · ", extra) + ")";
    }

    private static int contar(List<Venta> ventas, String estado) {
        return (int) ventas.stream().filter(v -> estado.equals(v.getEstadoPago())).count();
    }

    private static BigDecimal suma(List<Venta> ventas, Function<Venta, BigDecimal> campo) {
        return ventas.stream().map(campo).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumar(List<Puesto> ps, Function<Puesto, BigDecimal> campo) {
        return ps.stream().map(campo).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String unir(List<Puesto> ps, Function<Puesto, String> campo) {
        return ps.stream().map(campo).filter(Objects::nonNull).collect(Collectors.joining(", "));
    }

    private static String unirDistintos(List<Puesto> ps, Function<Puesto, String> campo) {
        return ps.stream().map(campo).filter(Objects::nonNull).distinct().collect(Collectors.joining(", "));
    }

    static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0 || parte == null) return BigDecimal.ZERO;
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    /** Formato es-BO: el punto agrupa y la coma separa decimales. */
    static String bs(BigDecimal v) {
        return String.format(Locale.of("es", "BO"), "%,.2f", v == null ? BigDecimal.ZERO : v);
    }

    private static List<String> noNulos(String... v) {
        List<String> r = new ArrayList<>();
        for (String s : v) if (s != null) r.add(s);
        return r;
    }

    private static boolean vacio(String v) {
        return v == null || v.isBlank();
    }

    /** Minusculas y sin tildes, para buscar "unión" escribiendo "union". */
    static String normal(String v) {
        if (vacio(v)) return null;
        return Normalizer.normalize(v.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static int entero(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static Long numero(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }

    private static BigDecimal decimal(Object v) {
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static String texto(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static LocalDateTime momento(Object v) {
        if (v instanceof Timestamp t) return t.toLocalDateTime();
        if (v instanceof LocalDateTime l) return l;
        if (v instanceof java.time.OffsetDateTime o) return o.toLocalDateTime();
        return null;
    }
}
