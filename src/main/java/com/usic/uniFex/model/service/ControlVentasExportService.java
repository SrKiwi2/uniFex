package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dto.ControlVentasDTO;
import com.usic.uniFex.model.dto.ControlVentasDTO.Categoria;
import com.usic.uniFex.model.dto.ControlVentasDTO.Puesto;
import com.usic.uniFex.model.dto.ControlVentasDTO.Responsable;
import com.usic.uniFex.model.dto.ControlVentasDTO.Resumen;
import com.usic.uniFex.model.dto.ControlVentasDTO.Venta;
import com.usic.uniFex.model.dto.ControlVentasDTO.Vendedor;
import com.usic.uniFex.model.service.ControlVentasService.Calculo;
import com.usic.uniFex.model.service.ControlVentasService.Filtros;
import com.usic.uniFex.model.service.TablaExportService.Columna;
import com.usic.uniFex.model.service.TablaExportService.Tabla;

import lombok.RequiredArgsConstructor;

/**
 * El Excel y el PDF de Control de ventas.
 *
 * El Excel lleva TODAS las celdas del registro —fechas y telefonos incluidos—, en una hoja por
 * nivel de detalle: la venta, cada caseta y cada responsable con su credencial. Una sola hoja
 * con todo obligaria a repetir la venta en cada caseta y cada responsable, y sumar esa columna
 * de importes daria el doble.
 *
 * El PDF es para imprimir y firmar. Sin filtro: resumen, rendicion por vendedor, categorias y
 * las ventas que siguen por rendir. <b>Filtrado por un vendedor es su hoja de rendicion de
 * cuentas</b>: sus cifras, cada una de sus ventas con su comprobante y un espacio de firmas.
 *
 * Todo sale de {@link ControlVentasService#calcular()}: lo mismo que ve la pantalla.
 */
@Service
@RequiredArgsConstructor
public class ControlVentasExportService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MOMENTO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ControlVentasService control;
    private final TablaExportService exportador;

    @Transactional(readOnly = true)
    public byte[] excel(Filtros filtros) {
        Calculo c = control.calcular();
        List<Venta> ventas = control.filtrar(c, filtros);
        List<String> notas = notas(filtros);
        notas.add(ventas.size() + " venta(s) en este reporte.");

        List<Tabla> hojas = new ArrayList<>();
        hojas.add(tablaResumen(control.resumen(ventas), notas));
        hojas.add(tablaVentas(ventas, notas));
        hojas.add(tablaPuestos(ventas.stream().flatMap(v -> c.puestosDe(v.getInscripcionId()).stream()).toList(), notas));
        hojas.add(tablaResponsables(ventas.stream()
                .flatMap(v -> c.responsablesDe(v.getInscripcionId()).stream()).toList(), notas));
        hojas.add(tablaPorRendir(ventas, notas));
        hojas.add(tablaVendedores(control.vendedores(ventas), notas));
        hojas.add(tablaCategorias(control.categorias(c, ventas), notas));
        return exportador.excel(hojas);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(Filtros filtros) {
        Calculo c = control.calcular();
        List<Venta> ventas = control.filtrar(c, filtros);
        List<String> notas = notas(filtros);
        List<Vendedor> vendedores = control.vendedores(ventas);

        List<Tabla> paginas = new ArrayList<>();
        if (filtros != null && filtros.vendedorId() != null && !vendedores.isEmpty()) {
            // Rendicion de cuentas de UN vendedor: lo que se imprime, se revisa y se firma.
            Vendedor v = vendedores.get(0);
            List<String> suyas = conNotas(notas, "Vendedor: " + v.vendedor()
                    + (v.usuario() == null ? "" : " (" + v.usuario() + ")"));
            paginas.add(tablaResumen(control.resumen(ventas), suyas, "Rendicion de cuentas - " + v.vendedor()));
            paginas.add(tablaDetalleVendedor(ventas, suyas, v));
            return exportador.pdf(paginas);
        }
        paginas.add(tablaResumen(control.resumen(ventas), notas));
        paginas.add(tablaVendedoresPdf(vendedores, notas));
        paginas.add(tablaCategorias(control.categorias(c, ventas), notas));
        paginas.add(tablaPorRendir(ventas, notas));
        return exportador.pdf(paginas);
    }

    // ------------------------------------------------------------------ hojas

    private Tabla tablaResumen(Resumen r, List<String> notas) {
        return tablaResumen(r, notas, "Resumen");
    }

    /** Dos columnas de valor —cantidad y Bs— para que Excel no muestre "272,00 ventas". */
    private Tabla tablaResumen(Resumen r, List<String> notas, String titulo) {
        List<List<String>> f = new ArrayList<>();
        f.add(fila("Operaciones (ventas)", ent(r.operaciones()), ""));
        f.add(fila("Expositores distintos", ent(r.expositores()), ""));
        f.add(fila("Vendedores con ventas", ent(r.vendedores()), ""));
        f.add(fila("Puestos vendidos / importe", ent(r.puestos()), bs(r.importePuestos())));
        f.add(fila("   de ellos exentos (sin costo)", ent(r.puestosExentos()), ""));
        f.add(fila("Credenciales extra vendidas / importe", ent(r.extras()), bs(r.importeExtras())));
        f.add(fila("TOTAL VENDIDO", "", bs(r.totalVendido())));
        f.add(fila("Recaudado por puestos (con comprobante)", "", bs(r.recaudadoPuestos())));
        f.add(fila("Recaudado por credenciales extra (con comprobante)", "", bs(r.recaudadoExtras())));
        f.add(fila("TOTAL RECAUDADO (RENDIDO)", "", bs(r.totalRecaudado())));
        f.add(fila("POR RENDIR (sin comprobante)", ent(r.ventasPorRendir() + r.ventasParciales()), bs(r.porRendir())));
        f.add(fila("Ventas con comprobante", ent(r.ventasConComprobante()), ""));
        f.add(fila("Ventas sin costo (exentas)", ent(r.ventasSinCosto()), ""));
        f.add(fila("Carpas que corresponden (puestos x 1)", ent(r.carpasCorresponden()), ""));
        f.add(fila("Credenciales incluidas (puestos x 2)", ent(r.credencialesIncluidas()), ""));
        f.add(fila("Credenciales extra", ent(r.credencialesExtras()), ""));
        f.add(fila("Total de credenciales que corresponden", ent(r.credencialesTotal()), ""));
        f.add(fila("Responsables registrados", ent(r.responsablesRegistrados()), ""));
        f.add(fila("Credenciales emitidas (virtual o impresa)", ent(r.credencialesEmitidas()), ""));
        return new Tabla(titulo, conNotas(notas,
                "Recaudado (rendido) = respaldado por el comprobante adjunto a la venta: foto del deposito, voucher o recibo. "
                        + "Banco y numero no se exigen.",
                "Por rendir = total vendido - total recaudado. Carpas = puestos x 1; credenciales incluidas = puestos x 2.",
                "Credencial emitida = generada al menos una vez (la virtual se enviaba por WhatsApp). El envio en si no quedo registrado."),
                List.of(Columna.texto("Indicador"), Columna.entero("Cantidad"), Columna.numero("Bs")), f);
    }

    private Tabla tablaVentas(List<Venta> ventas, List<String> notas) {
        List<Columna> cols = List.of(
                Columna.entero("N° venta"), Columna.texto("Código de venta"), Columna.texto("Fecha de venta"),
                Columna.texto("Vendedor"), Columna.texto("Usuario vendedor"),
                Columna.texto("Código expositor"), Columna.texto("Base del código"), Columna.entero("Compras del expositor"),
                Columna.texto("Entidad"), Columna.texto("Rubro"), Columna.texto("Tipo de entidad"), Columna.texto("NIT"),
                Columna.texto("Representante legal"), Columna.texto("C.I. representante"), Columna.texto("Celular representante"),
                Columna.texto("Titular"), Columna.texto("C.I. titular"), Columna.texto("Celular titular"),
                Columna.texto("Responsables (C.I. · celular)"),
                Columna.texto("Categoría"), Columna.texto("Subcategoría"), Columna.texto("Puestos"), Columna.texto("Ubicación"),
                Columna.entero("Cantidad de puestos"), Columna.numero("Precio unitario"), Columna.texto("Precios aplicados"),
                Columna.numero("Precio de lista"), Columna.numero("Descuento"), Columna.entero("Puestos exentos"),
                Columna.texto("Motivo de exención"), Columna.numero("Importe por puestos"),
                Columna.entero("Credenciales extra"), Columna.numero("Precio credencial extra"),
                Columna.numero("Importe credenciales extra"), Columna.texto("Nombres de extras"),
                Columna.numero("Total vendido"), Columna.numero("Recaudado puestos"), Columna.numero("Recaudado extras"),
                Columna.numero("Total recaudado"), Columna.numero("Por rendir"), Columna.texto("Estado"),
                Columna.texto("Forma de pago"), Columna.texto("Comprobante adjunto"), Columna.texto("Banco"),
                Columna.texto("N° comprobante"), Columna.entero("Extras sin comprobante"),
                Columna.entero("Carpas corresponden"), Columna.entero("Credenciales incluidas"),
                Columna.entero("Credenciales total"), Columna.entero("Responsables registrados"),
                Columna.entero("Credenciales emitidas"), Columna.texto("Fecha de emisión"),
                Columna.texto("Estado de la inscripción"));
        List<List<String>> filas = new ArrayList<>();
        for (Venta v : ventas) {
            filas.add(fila(
                    String.valueOf(v.getInscripcionId()), v.getCodigoVenta(), momento(v.getFecha()),
                    v.getVendedor(), v.getVendedorUsuario(),
                    v.getCodigoExpositor(), v.getBaseCodigo(), ent(v.getComprasExpositor()),
                    v.getEntidad(), v.getRubro(), v.getTipoEntidad(), v.getNit(),
                    v.getRepresentante(), v.getCiRepresentante(), v.getCelularRepresentante(),
                    v.getTitular(), v.getCiTitular(), v.getCelularTitular(), v.getResponsables(),
                    v.getCategorias(), v.getSubcategorias(), v.getPuestos(), v.getUbicacion(),
                    ent(v.getCantidadPuestos()),
                    v.getPrecioUnitario() == null ? "" : bs(v.getPrecioUnitario()), v.getPreciosUnitarios(),
                    bs(v.getPrecioLista()), bs(v.getDescuento()), ent(v.getPuestosExentos()),
                    v.getMotivoExencion(), bs(v.getImportePuestos()),
                    ent(v.getExtras()), bs(v.getPrecioExtra()), bs(v.getImporteExtras()), v.getNombresExtras(),
                    bs(v.getTotalVendido()), bs(v.getRecaudadoPuestos()), bs(v.getRecaudadoExtras()),
                    bs(v.getTotalRecaudado()), bs(v.getPorRendir()), estado(v.getEstadoPago()),
                    v.getFormaPago(), v.isConComprobante() ? "Sí" : "No", v.getEntidadBancaria(),
                    v.getNumComprobante(), ent(v.getExtrasSinComprobante()),
                    ent(v.getCarpasCorresponden()), ent(v.getCredencialesIncluidas()),
                    ent(v.getCredencialesTotal()), ent(v.getResponsablesRegistrados()),
                    ent(v.getCredencialesEmitidas()), dia(v.getFechaEmision()), v.getEstadoInscripcion()));
        }
        return new Tabla("Ventas", conNotas(notas,
                "Una fila por operacion. Precio unitario vacio = la venta tiene casetas de precios distintos (ver hoja Puestos).",
                "Codigo expositor: numero de NIT o, si no es valido, del C.I. del representante. Une las compras del mismo negocio.",
                "Descuento = precio de lista vigente de la tarifa aplicada - precio cobrado."),
                cols, filas);
    }

    private Tabla tablaPuestos(List<Puesto> puestos, List<String> notas) {
        List<List<String>> filas = new ArrayList<>();
        for (Puesto p : puestos) {
            filas.add(fila(String.valueOf(p.inscripcionId()), p.codigoVenta(), momento(p.fecha()), p.vendedor(),
                    p.codigoExpositor(), p.entidad(), p.codigo(), p.categoria(), p.subcategoria(),
                    bs(p.precioLista()), bs(p.precio()), bs(p.descuento()), p.exento() ? "Sí" : "No",
                    p.motivoExencion()));
        }
        return new Tabla("Puestos", conNotas(notas, "Una fila por caseta vendida, con el precio CONGELADO el dia de la venta."),
                List.of(Columna.entero("N° venta"), Columna.texto("Código de venta"), Columna.texto("Fecha"),
                        Columna.texto("Vendedor"), Columna.texto("Código expositor"), Columna.texto("Entidad"),
                        Columna.texto("Puesto"), Columna.texto("Categoría"), Columna.texto("Subcategoría"),
                        Columna.numero("Precio de lista"), Columna.numero("Precio unitario"),
                        Columna.numero("Descuento"), Columna.texto("Exento"), Columna.texto("Motivo de exención")),
                filas);
    }

    private Tabla tablaResponsables(List<Responsable> rs, List<String> notas) {
        List<List<String>> filas = new ArrayList<>();
        for (Responsable r : rs) {
            filas.add(fila(String.valueOf(r.inscripcionId()), r.codigoVenta(), r.entidad(), r.vendedor(), r.tipo(),
                    r.nombre(), r.ci(), r.celular(), momento(r.registrado()),
                    r.montoExtra() == null ? "" : bs(r.montoExtra()),
                    r.montoExtra() == null ? "" : (r.conComprobante() ? "Sí" : "No"),
                    r.credencialEmitida() ? "Sí" : "No", momento(r.emitidaEn()), r.emitidaPor(),
                    r.credencialEmitida() ? ent(r.vecesGenerada()) : ""));
        }
        return new Tabla("Responsables y credenciales", conNotas(notas,
                "Titular, acompañantes y credenciales extra de cada venta, con C.I., celular y su credencial.",
                "Emitida = su credencial se genero al menos una vez (la virtual es la que se enviaba por WhatsApp). "
                        + "Fecha y usuario de la PRIMERA vez; 'Veces' cuenta las regeneraciones."),
                List.of(Columna.entero("N° venta"), Columna.texto("Código de venta"), Columna.texto("Entidad"),
                        Columna.texto("Vendedor"), Columna.texto("Tipo"), Columna.texto("Nombre"),
                        Columna.texto("C.I."), Columna.texto("Celular"), Columna.texto("Registrado"),
                        Columna.numero("Monto extra"), Columna.texto("Comprobante extra"),
                        Columna.texto("Credencial emitida"), Columna.texto("Emitida el"),
                        Columna.texto("Emitida por"), Columna.entero("Veces generada")),
                filas);
    }

    /** Lo que falta rendir: las ventas sin comprobante (o con alguna extra sin el suyo). */
    private Tabla tablaPorRendir(List<Venta> ventas, List<String> notas) {
        List<List<String>> filas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Venta v : ventas) {
            if (v.getPorRendir().signum() <= 0) continue;
            total = total.add(v.getPorRendir());
            filas.add(fila(String.valueOf(v.getInscripcionId()), dia(v.getFecha() == null ? null : v.getFecha().toLocalDate()),
                    v.getVendedor(), v.getEntidad(), v.getRepresentante(), v.getCelularRepresentante(),
                    v.getPuestos(), v.getFormaPago(), bs(v.getTotalVendido()), bs(v.getTotalRecaudado()),
                    bs(v.getPorRendir()), motivoPorRendir(v)));
        }
        if (!filas.isEmpty()) {
            filas.add(fila("TOTAL", "", "", "", "", "", "", "", "", "", bs(total), ""));
        }
        return new Tabla("Por rendir", conNotas(notas,
                "Ventas a las que les falta el comprobante. Se rinden adjuntandolo (foto, voucher o recibo); banco y numero son opcionales."),
                List.of(Columna.entero("N° venta"), Columna.texto("Fecha"), Columna.texto("Vendedor"),
                        Columna.texto("Entidad"), Columna.texto("Representante"), Columna.texto("Celular"),
                        Columna.texto("Puestos"), Columna.texto("Forma de pago"), Columna.numero("Vendido"),
                        Columna.numero("Recaudado"), Columna.numero("Por rendir"), Columna.texto("Qué falta")),
                filas);
    }

    private Tabla tablaVendedores(List<Vendedor> vs, List<String> notas) {
        List<List<String>> filas = new ArrayList<>();
        for (Vendedor v : vs) {
            filas.add(fila(v.vendedor(), v.usuario(), ent(v.operaciones()), ent(v.expositores()), ent(v.puestos()),
                    bs(v.importePuestos()), bs(v.recaudadoPuestos()), ent(v.extras()), bs(v.importeExtras()),
                    bs(v.recaudadoExtras()), bs(v.totalVendido()), bs(v.totalRecaudado()), bs(v.porRendir()),
                    ent(v.ventasPorRendir()), pct(v.porcentajeVendido()), ent(v.credencialesTotal()),
                    ent(v.credencialesEmitidas())));
        }
        if (!vs.isEmpty()) {
            filas.add(fila("TOTAL", "", ent(vs.stream().mapToInt(Vendedor::operaciones).sum()), "",
                    ent(vs.stream().mapToInt(Vendedor::puestos).sum()), bs(sum(vs, Vendedor::importePuestos)),
                    bs(sum(vs, Vendedor::recaudadoPuestos)), ent(vs.stream().mapToInt(Vendedor::extras).sum()),
                    bs(sum(vs, Vendedor::importeExtras)), bs(sum(vs, Vendedor::recaudadoExtras)),
                    bs(sum(vs, Vendedor::totalVendido)), bs(sum(vs, Vendedor::totalRecaudado)),
                    bs(sum(vs, Vendedor::porRendir)), ent(vs.stream().mapToInt(Vendedor::ventasPorRendir).sum()),
                    "100,0 %", ent(vs.stream().mapToInt(Vendedor::credencialesTotal).sum()),
                    ent(vs.stream().mapToInt(Vendedor::credencialesEmitidas).sum())));
        }
        return new Tabla("Vendedores", conNotas(notas,
                "Rendicion de cuentas de cada vendedor: lo vendido, lo recaudado con comprobante y lo que falta rendir."),
                List.of(Columna.texto("Vendedor"), Columna.texto("Usuario"), Columna.entero("Operaciones"),
                        Columna.entero("Expositores"), Columna.entero("Puestos vendidos"),
                        Columna.numero("Vendido puestos"), Columna.numero("Recaudado puestos"),
                        Columna.entero("Extras vendidas"), Columna.numero("Vendido extras"),
                        Columna.numero("Recaudado extras"), Columna.numero("Total vendido"),
                        Columna.numero("Total recaudado"), Columna.numero("Por rendir"),
                        Columna.entero("Ventas por rendir"), Columna.numero("% del total vendido"),
                        Columna.entero("Credenciales corresponden"), Columna.entero("Credenciales emitidas")),
                filas);
    }

    /** La version impresa: sin las columnas que en papel apaisado ya no caben. */
    private Tabla tablaVendedoresPdf(List<Vendedor> vs, List<String> notas) {
        List<List<String>> filas = new ArrayList<>();
        for (Vendedor v : vs) {
            filas.add(fila(v.vendedor(), ent(v.operaciones()), ent(v.puestos()), bs(v.importePuestos()),
                    bs(v.recaudadoPuestos()), ent(v.extras()), bs(v.recaudadoExtras()), bs(v.totalVendido()),
                    bs(v.totalRecaudado()), bs(v.porRendir()), pct(v.porcentajeVendido())));
        }
        if (!vs.isEmpty()) {
            filas.add(fila("TOTAL", ent(vs.stream().mapToInt(Vendedor::operaciones).sum()),
                    ent(vs.stream().mapToInt(Vendedor::puestos).sum()), bs(sum(vs, Vendedor::importePuestos)),
                    bs(sum(vs, Vendedor::recaudadoPuestos)), ent(vs.stream().mapToInt(Vendedor::extras).sum()),
                    bs(sum(vs, Vendedor::recaudadoExtras)), bs(sum(vs, Vendedor::totalVendido)),
                    bs(sum(vs, Vendedor::totalRecaudado)), bs(sum(vs, Vendedor::porRendir)), "100,0 %"));
        }
        return new Tabla("Rendicion por vendedor", conNotas(notas,
                "Recaudado = con comprobante adjunto. Por rendir = vendido - recaudado."),
                List.of(Columna.texto("Vendedor"), Columna.entero("Operac."), Columna.entero("Puestos"),
                        Columna.numero("Vendido puestos"), Columna.numero("Recaudado puestos"),
                        Columna.entero("Extras"), Columna.numero("Recaudado extras"),
                        Columna.numero("Total vendido"), Columna.numero("Total recaudado"),
                        Columna.numero("Por rendir"), Columna.numero("% del total")),
                filas);
    }

    /** Cada venta de UN vendedor, para su hoja de rendicion. Con espacio de firmas al pie. */
    private Tabla tablaDetalleVendedor(List<Venta> ventas, List<String> notas, Vendedor v) {
        List<List<String>> filas = new ArrayList<>();
        for (Venta x : ventas) {
            filas.add(fila(String.valueOf(x.getInscripcionId()),
                    dia(x.getFecha() == null ? null : x.getFecha().toLocalDate()), x.getEntidad(),
                    x.getPuestos(), ent(x.getCantidadPuestos()), bs(x.getImportePuestos()),
                    ent(x.getExtras()), bs(x.getImporteExtras()), bs(x.getTotalVendido()),
                    x.isConComprobante() ? "Sí" : "No", bs(x.getTotalRecaudado()), bs(x.getPorRendir())));
        }
        filas.add(fila("TOTAL", "", ent(v.operaciones()) + " venta(s)", "", ent(v.puestos()),
                bs(v.importePuestos()), ent(v.extras()), bs(v.importeExtras()), bs(v.totalVendido()), "",
                bs(v.totalRecaudado()), bs(v.porRendir())));
        // Firmas: una fila con espacio en blanco al pie, que en papel queda como casilla. Van en
        // columnas de TEXTO (Entidad y Comprobante): en una numerica se alinean a la derecha y
        // se parten en cuatro renglones.
        filas.add(fila("", "", "\n\n\n\nEntregué conforme:\n" + v.vendedor(), "", "", "", "", "", "",
                "\n\n\n\nRecibí conforme:\nAdministración", "", ""));
        return new Tabla("Detalle de ventas - " + v.vendedor(), conNotas(notas,
                "Comprobante = foto del deposito, voucher o recibo adjunto a la venta. Por rendir = lo que aun no tiene comprobante."),
                List.of(Columna.entero("N° venta"), Columna.texto("Fecha"), Columna.texto("Entidad"),
                        Columna.texto("Puestos"), Columna.entero("Cant."), Columna.numero("Puestos Bs"),
                        Columna.entero("Extras"), Columna.numero("Extras Bs"), Columna.numero("Total vendido"),
                        Columna.texto("Comprobante"), Columna.numero("Recaudado"), Columna.numero("Por rendir")),
                filas);
    }

    private Tabla tablaCategorias(List<Categoria> cs, List<String> notas) {
        List<List<String>> filas = new ArrayList<>();
        for (Categoria c : cs) {
            filas.add(fila(c.categoria(), ent(c.puestosVendidos()), ent(c.puestosTotales()),
                    pct(c.porcentajeOcupacion()), pct(c.porcentajePuestos()), ent(c.exentos()),
                    bs(c.vendidoBs()), pct(c.porcentajeIngresos()), bs(c.recaudadoBs())));
        }
        if (!cs.isEmpty()) {
            int vendidas = cs.stream().mapToInt(Categoria::puestosVendidos).sum();
            int total = cs.stream().mapToInt(Categoria::puestosTotales).sum();
            filas.add(fila("TOTAL", ent(vendidas), ent(total),
                    pct(ControlVentasService.porcentaje(BigDecimal.valueOf(vendidas), BigDecimal.valueOf(total))),
                    "100,0 %", ent(cs.stream().mapToInt(Categoria::exentos).sum()),
                    bs(sum(cs, Categoria::vendidoBs)), "100,0 %", bs(sum(cs, Categoria::recaudadoBs))));
        }
        return new Tabla("Categorias", conNotas(notas,
                "Ventas de puestos por categoria (sin credenciales extra, que no tienen categoria).",
                "Vendible = casetas del plano menos las bloqueadas. Recaudado: lo respaldado con comprobante."),
                List.of(Columna.texto("Categoría"), Columna.entero("Puestos vendidos"), Columna.entero("Puestos vendibles"),
                        Columna.numero("% ocupación"), Columna.numero("% de los puestos vendidos"),
                        Columna.entero("Exentos"), Columna.numero("Vendido Bs"), Columna.numero("% de ingresos"),
                        Columna.numero("Recaudado Bs")),
                filas);
    }

    // ------------------------------------------------------------------ formato

    private static String motivoPorRendir(Venta v) {
        List<String> r = new ArrayList<>();
        if (!v.isConComprobante() && v.getImportePuestos().signum() > 0) {
            r.add("comprobante de la venta" + ("Al contado".equals(v.getFormaPago()) ? " (marcada al contado)" : ""));
        }
        if (v.getExtrasSinComprobante() > 0) {
            r.add(v.getExtrasSinComprobante() + " credencial(es) extra sin comprobante");
        }
        return String.join("; ", r);
    }

    /** Las notas comunes (edicion y filtros) seguidas de las propias de la hoja. */
    private static List<String> conNotas(List<String> comunes, String... propias) {
        List<String> r = new ArrayList<>(comunes);
        r.addAll(List.of(propias));
        return r;
    }

    private static List<String> notas(Filtros f) {
        List<String> r = new ArrayList<>();
        r.add("Edicion activa. Solo operaciones vigentes: no incluye ventas canceladas ni casetas anuladas.");
        if (f != null) {
            List<String> partes = new ArrayList<>();
            if (f.desde() != null) partes.add("desde " + f.desde().format(DIA));
            if (f.hasta() != null) partes.add("hasta " + f.hasta().format(DIA));
            if (f.categoriaId() != null) partes.add("una categoria (id " + f.categoriaId() + ")");
            if (f.estadoPago() != null && !f.estadoPago().isBlank()) partes.add("estado " + estado(f.estadoPago()));
            if (f.texto() != null && !f.texto().isBlank()) partes.add("busqueda \"" + f.texto().trim() + "\"");
            if (!partes.isEmpty()) r.add("Filtro: " + String.join(", ", partes) + ".");
        }
        return r;
    }

    private static String estado(String e) {
        if (e == null) return "";
        return switch (e.toUpperCase(java.util.Locale.ROOT)) {
            case ControlVentasDTO.PAGADO -> "Con comprobante";
            case ControlVentasDTO.PARCIAL -> "Comprobante parcial";
            case ControlVentasDTO.PENDIENTE -> "Sin comprobante";
            case ControlVentasDTO.SIN_COSTO -> "Sin costo";
            default -> e;
        };
    }

    private static List<String> fila(String... celdas) {
        List<String> r = new ArrayList<>(celdas.length);
        for (String c : celdas) r.add(c == null ? "" : c);
        return r;
    }

    private static <T> BigDecimal sum(List<T> xs, java.util.function.Function<T, BigDecimal> f) {
        return xs.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String bs(BigDecimal v) {
        return ControlVentasService.bs(v);
    }

    private static String ent(int v) {
        return String.valueOf(v);
    }

    private static String pct(BigDecimal v) {
        return String.format(java.util.Locale.of("es", "BO"), "%,.1f %%", v == null ? BigDecimal.ZERO : v);
    }

    private static String dia(LocalDate d) {
        return d == null ? "" : d.format(DIA);
    }

    private static String momento(LocalDateTime d) {
        return d == null ? "" : d.format(MOMENTO);
    }
}
