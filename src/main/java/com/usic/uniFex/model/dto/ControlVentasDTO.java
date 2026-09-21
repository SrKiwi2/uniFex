package com.usic.uniFex.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Lo que devuelve el modulo "Control de ventas" (rendicion de cuentas de la feria).
 *
 * Todo sale de UN calculo ({@code ControlVentasService.calcular}): la pantalla, el Excel y el
 * PDF leen las mismas filas. Si cada salida recalculara por su cuenta, el papel acabaria
 * diciendo un monto por rendir y la pantalla otro.
 *
 * <h2>Vocabulario</h2>
 * <ul>
 *   <li><b>Vendido</b>: el importe de la venta (puestos al precio congelado + credenciales
 *       extra).</li>
 *   <li><b>Recaudado</b> (= rendido): lo respaldado por un comprobante adjunto — la foto del
 *       deposito, el voucher, el recibo. Banco y numero NO son obligatorios: el archivo
 *       basta.</li>
 *   <li><b>Por rendir</b>: lo vendido que todavia no tiene comprobante.</li>
 * </ul>
 */
public final class ControlVentasDTO {

    private ControlVentasDTO() {
    }

    /** Estados de una venta segun su comprobante. */
    public static final String PAGADO = "PAGADO";
    /** Puestos con comprobante pero alguna credencial extra sin el suyo (o al reves). */
    public static final String PARCIAL = "PARCIAL";
    public static final String PENDIENTE = "PENDIENTE";
    /** Venta de importe cero (casetas exentas, sin extras): no hay nada que rendir. */
    public static final String SIN_COSTO = "SIN_COSTO";

    /**
     * Una venta (operacion) con todas sus celdas: lo registrado y lo que se calcula.
     *
     * Clase y no {@code record}: son medio centenar de campos, y un constructor canonico de
     * cincuenta argumentos posicionales es la forma mas segura de cruzar dos importes sin que
     * el compilador diga nada.
     */
    @Getter
    @Setter
    public static class Venta {
        // --- la operacion
        private Long inscripcionId;
        private String codigoVenta;
        private LocalDateTime fecha;
        private String estadoInscripcion;

        // --- quien la hizo
        private Long vendedorId;
        private String vendedor;
        private String vendedorUsuario;

        // --- el expositor
        private String codigoExpositor;
        /** De donde sale el codigo: NIT, CI del representante o la ficha de la entidad. */
        private String baseCodigo;
        /** Cuantas ventas vivas de la edicion comparten este codigo. */
        private int comprasExpositor;
        private Long entidadId;
        private String entidad;
        private String rubro;
        private String tipoEntidad;
        private String nit;
        private String representante;
        private String ciRepresentante;
        private String celularRepresentante;
        private String titular;
        private String ciTitular;
        private String celularTitular;
        /** "Nombre (C.I. · cel.)" de los responsables que NO son extra, separados por "; ". */
        private String responsables;
        private String nombresExtras;

        // --- las casetas
        private String categorias;
        private String subcategorias;
        private String puestos;
        private String ubicacion;
        private int cantidadPuestos;
        /** El precio aplicado si todas las casetas costaron lo mismo; null si hay varios. */
        private BigDecimal precioUnitario;
        /** Los precios aplicados, en texto ("150,00" o "150,00 / 300,00"). */
        private String preciosUnitarios;
        private BigDecimal precioLista;
        private BigDecimal descuento;
        private int puestosExentos;
        private String motivoExencion;
        private BigDecimal importePuestos;

        // --- credenciales extra
        private int extras;
        private BigDecimal precioExtra;
        private BigDecimal importeExtras;
        /** Extras cobradas cuyo comprobante falta. */
        private int extrasSinComprobante;

        // --- dinero
        private BigDecimal totalVendido;
        private BigDecimal recaudadoPuestos;
        private BigDecimal recaudadoExtras;
        private BigDecimal totalRecaudado;
        private BigDecimal porRendir;
        private String estadoPago;
        /** "Al contado" o "Deposito / transferencia", tal como se marco al vender. */
        private String formaPago;
        private String entidadBancaria;
        private String numComprobante;
        private boolean conComprobante;
        private String comprobanteUrl;

        // --- carpas y credenciales
        private int carpasCorresponden;
        private int credencialesIncluidas;
        private int credencialesTotal;
        /** Personas registradas como responsables (titular, acompañantes y extras). */
        private int responsablesRegistrados;
        /** Responsables con su credencial generada (virtual, para WhatsApp, o impresa). */
        private int credencialesEmitidas;
        private LocalDate fechaEmision;

        public int getCredencialesPendientes() {
            return Math.max(0, credencialesTotal - credencialesEmitidas);
        }
    }

    /** Una caseta vendida: el nivel al que tiene sentido el "precio unitario". */
    public record Puesto(
            Long inscripcionId, String codigoVenta, LocalDateTime fecha, String vendedor,
            String codigoExpositor, String entidad, Long puestoId, String codigo, String categoria,
            String subcategoria, BigDecimal precioLista, BigDecimal precio, BigDecimal descuento,
            boolean exento, String motivoExencion) {
    }

    /**
     * Un responsable de una venta, con sus datos de contacto y su credencial.
     *
     * {@code emitidaEn}/{@code emitidaPor}: la PRIMERA vez que se genero su credencial. La
     * credencial virtual se generaba para mandarla por WhatsApp, normalmente el mismo promotor
     * de la venta; que el envio llegara no quedo registrado en ningun sitio.
     */
    public record Responsable(
            Long inscripcionId, String codigoVenta, String entidad, String vendedor,
            Long responsableId, String tipo, String nombre, String ci, String celular,
            BigDecimal montoExtra, boolean conComprobante, String comprobanteUrl,
            LocalDateTime registrado, boolean credencialEmitida, LocalDateTime emitidaEn,
            String emitidaPor, int vecesGenerada) {
    }

    /** Rendicion de cuentas de un vendedor sobre sus operaciones vigentes. */
    public record Vendedor(
            Long usuarioId, String vendedor, String usuario, int operaciones, int expositores,
            int puestos, int extras, BigDecimal importePuestos, BigDecimal importeExtras,
            BigDecimal totalVendido, BigDecimal recaudadoPuestos, BigDecimal recaudadoExtras,
            BigDecimal totalRecaudado, BigDecimal porRendir, int ventasPorRendir,
            BigDecimal porcentajeVendido, int credencialesTotal, int credencialesEmitidas) {
    }

    /** Ventas de casetas por categoria. */
    public record Categoria(
            String categoria, int puestosVendidos, int puestosTotales, BigDecimal porcentajePuestos,
            BigDecimal porcentajeOcupacion, BigDecimal vendidoBs, BigDecimal porcentajeIngresos,
            BigDecimal recaudadoBs, int exentos) {
    }

    public record Dia(LocalDate fecha, int operaciones, int puestos, BigDecimal totalBs,
                      BigDecimal acumuladoBs) {
    }

    /** Las cifras de cabecera del tablero, sobre las ventas filtradas. */
    public record Resumen(
            int operaciones, int expositores, int vendedores, int puestos, int puestosExentos,
            int extras, BigDecimal importePuestos, BigDecimal importeExtras,
            BigDecimal totalVendido, BigDecimal recaudadoPuestos, BigDecimal recaudadoExtras,
            BigDecimal totalRecaudado, BigDecimal porRendir,
            int ventasConComprobante, int ventasParciales, int ventasPorRendir, int ventasSinCosto,
            int carpasCorresponden, int credencialesIncluidas, int credencialesExtras,
            int credencialesTotal, int responsablesRegistrados, int credencialesEmitidas) {
    }

    public record Tablero(Resumen resumen, List<Vendedor> vendedores, List<Categoria> categorias,
                          List<Dia> avance) {
    }

    /** Todo lo de una venta, para su ficha. */
    public record Ficha(Venta venta, List<Puesto> puestos, List<Responsable> responsables) {
    }

    public record Opcion(Long id, String nombre) {
    }
}
