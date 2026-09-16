package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dto.AnalisisDTO;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Los analisis que mira quien dirige la feria: cuanto queda por vender, quien vende, cuanto se
 * cobro de verdad y a que ritmo se avanza.
 *
 * <h2>Dos reglas que comparten las cuatro consultas</h2>
 *
 * <b>1. Solo la edicion activa.</b> Todas filtran por
 * {@code id_edicion = (select id from edicion where activa)}. Sin eso, en cuanto exista FEXPO
 * 2027 los totales sumarian los dos años y el error no se veria hasta que la cifra fuera
 * absurda. El reporte de ventas que ya existia no lo hacia; se corrigio al escribir esto.
 *
 * <b>2. Nada anulado.</b> Se descartan las inscripciones y los detalles con {@code _estado='X'}
 * y las casetas dadas de baja. Una venta cancelada no es dinero, y una caseta anulada no es
 * inventario: contarlas da un plano mas grande del que existe.
 *
 * <h2>Por que SQL nativo y no JPQL</h2>
 * Son agregados sobre cuatro o cinco tablas con conteos condicionales
 * ({@code count(*) filter (where ...)}). Escribirlos con entidades traeria las filas a memoria
 * para contarlas en Java: 555 casetas y 106 detalles hoy, y toda la feria el dia de mañana.
 */
@Service
public class AnalisisVentasService {

    @PersistenceContext
    private EntityManager em;

    /** La edicion activa, como subconsulta. Se repite en todas: es la regla, no un adorno. */
    private static final String EDICION_ACTIVA =
            "(select ed.id from edicion ed where ed.activa limit 1)";

    /**
     * Las tablas de una venta que cuenta. Va SIN el {@code where} para que quien lo use pueda
     * añadir sus propios JOIN detras: un WHERE en medio dejaria la consulta mal formada, y eso
     * no lo caza el compilador — revienta en ejecucion.
     *
     * Existen {@code inscripcion_puesto} con {@code id_puesto} nulo —ventas huerfanas, ya
     * documentadas— y colarlas descuadra el total contra el desglose.
     */
    private static final String DESDE_VENTAS = """
              from inscripcion i
              join inscripcion_puesto ip on ip.id_inscripcion = i.id and ip.id_puesto is not null
                   and (ip."_estado" is null or ip."_estado" <> 'X')
              join puesto p on p.id = ip.id_puesto
            """;

    /** Vivas y de la edicion activa. Se pega detras de los JOIN que añada cada consulta. */
    private static final String SOLO_VIVAS_EDICION_ACTIVA = """
             where (i."_estado" is null or i."_estado" <> 'X')
               and i.id_edicion = """ + EDICION_ACTIVA;

    // ------------------------------------------------------------------ ocupacion del plano

    /**
     * Cuanto queda por vender, por categoria.
     *
     * El dinero se calcula de dos formas distintas a proposito:
     * <ul>
     *   <li><b>vendido</b>: de {@code inscripcion_puesto.costo}, que es el precio CONGELADO el
     *       dia de la venta. Es lo que de verdad se cobro.</li>
     *   <li><b>por vender</b>: del precio VIGENTE de cada caseta libre —el suyo propio si lo
     *       tiene (V37), si no el de su categoria—. Es lo que entraria hoy.</li>
     * </ul>
     * Usar el precio de hoy para lo ya vendido haria que subir una tarifa cambiara el historico.
     */
    @Transactional(readOnly = true)
    public List<AnalisisDTO.Ocupacion> ocupacion() {
        String sql = """
                with vendido as (
                    select p.id_categoria, sum(ip.costo) as bs
                      from inscripcion i
                      join inscripcion_puesto ip on ip.id_inscripcion = i.id and ip.id_puesto is not null
                           and (ip."_estado" is null or ip."_estado" <> 'X')
                      join puesto p on p.id = ip.id_puesto
                     where (i."_estado" is null or i."_estado" <> 'X')
                       and i.id_edicion = %s
                     group by p.id_categoria
                )
                select c.nombre,
                       count(*) filter (where p.estado_puesto = 'O')::int as vendidas,
                       count(*) filter (where p.estado_puesto = 'T')::int as tramite,
                       count(*) filter (where p.estado_puesto = 'L')::int as libres,
                       count(*) filter (where p.estado_puesto = 'X')::int as bloqueadas,
                       count(*)::int as total,
                       coalesce(v.bs, 0) as bs_vendido,
                       coalesce(sum(coalesce(p.precio, c.precio_base, 0))
                                filter (where p.estado_puesto in ('L', 'T')), 0) as bs_por_vender
                  from puesto p
                  join categoria c on c.id = p.id_categoria
                  left join vendido v on v.id_categoria = c.id
                 where (p."_estado" is null or p."_estado" <> 'X')
                   and (c."_estado" is null or c."_estado" <> 'X')
                 group by c.id, c.nombre, v.bs
                 order by c.nombre
                """.formatted(EDICION_ACTIVA);

        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery(sql).getResultList();
        List<AnalisisDTO.Ocupacion> r = new ArrayList<>();
        for (Object[] f : filas) {
            int vendidas = entero(f[1]);
            int tramite = entero(f[2]);
            int libres = entero(f[3]);
            int bloqueadas = entero(f[4]);
            int total = entero(f[5]);
            // El porcentaje se mide sobre lo VENDIBLE, no sobre el total: una categoria con la
            // mitad bloqueada por obra no esta al 50 % de sus ventas, esta al 100 % de lo que
            // se podia vender, y presentarla como floja seria acusar al vendedor de algo ajeno.
            int vendible = total - bloqueadas;
            BigDecimal pct = vendible <= 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(vendidas * 100.0 / vendible).setScale(1, RoundingMode.HALF_UP);
            r.add(new AnalisisDTO.Ocupacion(texto(f[0]), vendidas, tramite, libres, bloqueadas,
                    total, pct, decimal(f[6]), decimal(f[7])));
        }
        return r;
    }

    // ------------------------------------------------------------------ ranking de vendedores

    /**
     * Quien vende cuanto, con su area y carrera (V35).
     *
     * `ventas` cuenta inscripciones DISTINTAS y `casetas` cuenta detalles: una venta de tres
     * casetas es una venta y tres casetas. Sin el {@code distinct}, cada caseta contaria como
     * una venta y el ranking premiaria a quien vende lotes grandes dos veces.
     */
    @Transactional(readOnly = true)
    public List<AnalisisDTO.Vendedor> vendedores() {
        String sql = """
                select u.id,
                       coalesce(nullif(btrim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)), ''), u.username),
                       a.sigla, ca.nombre,
                       count(distinct i.id)::int as ventas,
                       count(ip.id)::int as casetas,
                       coalesce(sum(ip.costo), 0) as total_bs
                """ + DESDE_VENTAS + """
                  join usuario u on u.id = i."_registro_id_usuario"
                  left join persona pe on pe.id = u.persona_id
                  left join carrera ca on ca.id = pe.id_carrera
                  left join area a on a.id = ca.id_area
                """ + SOLO_VIVAS_EDICION_ACTIVA + """
                 group by u.id, pe.nombre, pe.paterno, pe.materno, u.username, a.sigla, ca.nombre
                 order by total_bs desc, casetas desc
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery(sql).getResultList();
        List<AnalisisDTO.Vendedor> r = new ArrayList<>();
        for (Object[] f : filas) {
            int ventas = entero(f[4]);
            BigDecimal total = decimal(f[6]);
            BigDecimal medio = ventas == 0 ? BigDecimal.ZERO
                    : total.divide(BigDecimal.valueOf(ventas), 2, RoundingMode.HALF_UP);
            r.add(new AnalisisDTO.Vendedor(numero(f[0]), texto(f[1]), texto(f[2]), texto(f[3]),
                    ventas, entero(f[5]), total, medio));
        }
        return r;
    }

    // ------------------------------------------------------------------ por facultad

    /**
     * Lo vendido por la facultad (area academica) del vendedor que registro cada venta.
     *
     * El area se deduce por la carrera de la persona (V35) y no se copia a ningun sitio: por eso
     * mover una carrera de area corrige el historico entero sin tocar una sola venta.
     *
     * <b>Quien no tiene carrera asignada sale agrupado aparte, no se descarta.</b> Descartarlo
     * haria que la suma del reporte no cuadrara con el total de la feria, y un descuadre asi es
     * el que nadie sabe explicar tres meses despues. Ademas hoy es el caso MAYORITARIO: el
     * catalogo se sembro con V35 pero las carreras se asignan a mano desde la pantalla de
     * Vendedores, asi que mientras nadie lo haga este reporte sale entero en esa fila. No es un
     * fallo del reporte; es que falta el dato.
     */
    @Transactional(readOnly = true)
    public List<AnalisisDTO.Facultad> porFacultad() {
        String sql = """
                select a.sigla, a.nombre,
                       count(distinct u.id)::int as vendedores,
                       count(distinct i.id)::int as ventas,
                       count(ip.id)::int as casetas,
                       coalesce(sum(ip.costo), 0) as total_bs
                """ + DESDE_VENTAS + """
                  join usuario u on u.id = i."_registro_id_usuario"
                  left join persona pe on pe.id = u.persona_id
                  left join carrera ca on ca.id = pe.id_carrera
                  left join area a on a.id = ca.id_area
                """ + SOLO_VIVAS_EDICION_ACTIVA + """
                 group by a.sigla, a.nombre
                 order by total_bs desc
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery(sql).getResultList();

        BigDecimal total = filas.stream()
                .map(f -> decimal(f[5]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AnalisisDTO.Facultad> r = new ArrayList<>();
        for (Object[] f : filas) {
            BigDecimal bs = decimal(f[5]);
            BigDecimal pct = total.signum() == 0 ? BigDecimal.ZERO
                    : bs.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
            String sigla = texto(f[0]);
            r.add(new AnalisisDTO.Facultad(
                    sigla == null ? "SIN CARRERA" : sigla,
                    // El nombre largo arranca igual que la sigla a proposito (V35): los oficiales
                    // no estaban confirmados. Si siguen iguales, no se repite en pantalla.
                    nombreArea(sigla, texto(f[1])),
                    entero(f[2]), entero(f[3]), entero(f[4]), bs, pct));
        }
        return r;
    }

    /** El nombre del area solo cuando aporta algo sobre la sigla. */
    private static String nombreArea(String sigla, String nombre) {
        if (sigla == null) return "Sin carrera asignada";
        if (nombre == null || nombre.equalsIgnoreCase(sigla)) return null;
        return nombre;
    }

    // ------------------------------------------------------------------ vendido por categoria

    /**
     * Lo VENDIDO de cada categoria, en casetas y en dinero.
     *
     * Se deriva de {@link #ocupacion()} en vez de consultar aparte: es el mismo dato mirado sin
     * lo que queda por vender. Con dos consultas distintas, el dia que una cambie de criterio
     * —un filtro de estado, una anulacion— las dos pantallas diran numeros distintos y no habra
     * forma de saber cual creer.
     *
     * Salen tambien las categorias con CERO vendidas. Es informacion: una categoria que no ha
     * vendido nada es justamente la que hay que mirar, y esconderla la vuelve invisible.
     */
    @Transactional(readOnly = true)
    public List<AnalisisDTO.VendidoCategoria> vendidoPorCategoria() {
        List<AnalisisDTO.Ocupacion> base = ocupacion();
        BigDecimal total = base.stream()
                .map(AnalisisDTO.Ocupacion::bsVendido)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return base.stream()
                .sorted((a, b) -> b.bsVendido().compareTo(a.bsVendido()))
                .map(o -> new AnalisisDTO.VendidoCategoria(
                        o.categoria(), o.vendidas(), o.total(), o.porcentajeVendido(), o.bsVendido(),
                        total.signum() == 0 ? BigDecimal.ZERO
                                : o.bsVendido().multiply(BigDecimal.valueOf(100))
                                        .divide(total, 1, RoundingMode.HALF_UP)))
                .toList();
    }

    // ------------------------------------------------------------------ cobros

    /**
     * Cuanto se cobro de verdad y cuanto falta.
     *
     * <b>Con comprobante</b> es el corte que manda, no la forma de pago: marcar "contado" dice
     * COMO se pago, no que el recibo exista. Mientras eso conto como pagado se emitieron
     * credenciales a gente que nunca entrego el papel.
     */
    @Transactional(readOnly = true)
    public AnalisisDTO.Cobros cobros() {
        String cortes = """
                select case
                         when (i.img_comprobante is not null and i.img_comprobante <> '') then 'Con comprobante'
                         when i.pago_contado then 'Contado, SIN comprobante'
                         else 'Sin comprobante'
                       end as concepto,
                       count(distinct i.id)::int, count(ip.id)::int, coalesce(sum(ip.costo), 0)
                """ + DESDE_VENTAS + SOLO_VIVAS_EDICION_ACTIVA + """
                 group by 1
                 order by 1
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> filasCortes = em.createNativeQuery(cortes).getResultList();
        List<AnalisisDTO.Cobro> lista = new ArrayList<>();
        BigDecimal cobrado = BigDecimal.ZERO;
        BigDecimal pendiente = BigDecimal.ZERO;
        for (Object[] f : filasCortes) {
            AnalisisDTO.Cobro c = new AnalisisDTO.Cobro(texto(f[0]), entero(f[1]), entero(f[2]), decimal(f[3]));
            lista.add(c);
            if ("Con comprobante".equals(c.concepto())) cobrado = cobrado.add(c.totalBs());
            else pendiente = pendiente.add(c.totalBs());
        }

        String detalle = """
                select i.id, e.nombre,
                       coalesce(nullif(btrim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)), ''), u.username),
                       i.pago_contado, coalesce(sum(ip.costo), 0), date(i.fecha_compra),
                       greatest(0, date_part('day', now() - i.fecha_compra))::int
                """ + DESDE_VENTAS + """
                  join entidad e on e.id = i.id_entidad
                  left join usuario u on u.id = i."_registro_id_usuario"
                  left join persona pe on pe.id = u.persona_id
                """ + SOLO_VIVAS_EDICION_ACTIVA + """
                   and (i.img_comprobante is null or i.img_comprobante = '')
                 group by i.id, e.nombre, pe.nombre, pe.paterno, pe.materno, u.username,
                          i.pago_contado, i.fecha_compra
                 order by i.fecha_compra asc nulls last
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> filasDetalle = em.createNativeQuery(detalle).getResultList();
        List<AnalisisDTO.Pendiente> pendientes = new ArrayList<>();
        for (Object[] f : filasDetalle) {
            pendientes.add(new AnalisisDTO.Pendiente(numero(f[0]), texto(f[1]), texto(f[2]),
                    Boolean.TRUE.equals(f[3]), decimal(f[4]), fecha(f[5]), entero(f[6])));
        }
        return new AnalisisDTO.Cobros(lista, pendientes, cobrado, pendiente);
    }

    // ------------------------------------------------------------------ avance en el tiempo

    /**
     * Lo vendido cada dia, con el acumulado.
     *
     * Solo salen los dias CON ventas: rellenar los vacios con ceros duplicaria las filas de una
     * feria que se prepara durante meses y vende en semanas, y la grafica del cliente ya
     * dibuja el hueco por las fechas.
     *
     * El acumulado se suma aqui y no en SQL con una ventana: son decenas de filas y asi la
     * consulta se lee de un vistazo.
     */
    @Transactional(readOnly = true)
    public List<AnalisisDTO.Dia> avance() {
        String sql = """
                select date(i.fecha_compra) as dia,
                       count(distinct i.id)::int, count(ip.id)::int, coalesce(sum(ip.costo), 0)
                """ + DESDE_VENTAS + SOLO_VIVAS_EDICION_ACTIVA + """
                   and i.fecha_compra is not null
                 group by 1
                 order by 1
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery(sql).getResultList();
        List<AnalisisDTO.Dia> r = new ArrayList<>();
        BigDecimal acumBs = BigDecimal.ZERO;
        int acumCasetas = 0;
        for (Object[] f : filas) {
            BigDecimal bs = decimal(f[3]);
            int casetas = entero(f[2]);
            acumBs = acumBs.add(bs);
            acumCasetas += casetas;
            r.add(new AnalisisDTO.Dia(fecha(f[0]), entero(f[1]), casetas, bs, acumBs, acumCasetas));
        }
        return r;
    }

    // ------------------------------------------------------------------ conversiones

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

    private static LocalDate fecha(Object v) {
        if (v instanceof Date d) return d.toLocalDate();
        if (v instanceof Timestamp t) return t.toLocalDateTime().toLocalDate();
        if (v instanceof LocalDate l) return l;
        return null;
    }
}
