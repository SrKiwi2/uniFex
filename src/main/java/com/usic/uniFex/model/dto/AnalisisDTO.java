package com.usic.uniFex.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Los cuatro analisis de direccion, cada uno con la forma que se imprime.
 *
 * Son records y no mapas sueltos porque los consumen tres cosas a la vez —la pantalla, el PDF
 * y el Excel— y con mapas el nombre de una columna se escribe tres veces y se equivoca en dos.
 *
 * Todos miran SOLO la edicion activa. Esa decision no es un detalle: sin ella, en cuanto exista
 * FEXPO 2027 los totales sumarian los dos años y nadie lo notaria hasta que el numero fuera
 * absurdo. El reporte de ventas que ya existia tenia justo ese fallo.
 */
public final class AnalisisDTO {

    private AnalisisDTO() {
    }

    /**
     * Cuanto queda por vender, por categoria.
     *
     * {@code bsPorVender} es el precio vigente de lo que sigue libre, no una prediccion: es lo
     * que entraria si se vendiera todo lo que queda al precio de hoy. Se calcula con el precio
     * propio de cada caseta cuando lo tiene (V37), igual que cobra la venta.
     *
     * Las bloqueadas van aparte de las libres a proposito: una caseta en reparacion no es
     * dinero disponible, y sumarla al "por vender" inflaria la cifra que mira el jefe.
     */
    public record Ocupacion(
            String categoria,
            int vendidas,
            int enTramite,
            int libres,
            int bloqueadas,
            int total,
            /** Vendidas sobre el total vendible (excluye bloqueadas). 0..100. */
            BigDecimal porcentajeVendido,
            BigDecimal bsVendido,
            BigDecimal bsPorVender) {
    }

    /**
     * Quien vende cuanto.
     *
     * {@code ventas} son inscripciones y {@code casetas} son casetas: una venta de tres casetas
     * es UNA venta y TRES casetas, y confundirlas cambia el ranking. El ticket medio se calcula
     * por venta, que es como se compara a dos vendedores.
     */
    public record Vendedor(
            Long usuarioId,
            String vendedor,
            String area,
            String carrera,
            int ventas,
            int casetas,
            BigDecimal totalBs,
            BigDecimal ticketMedio) {
    }

    /** Un corte del cobro: contado, deposito, o lo que todavia no tiene comprobante. */
    public record Cobro(
            String concepto,
            int ventas,
            int casetas,
            BigDecimal totalBs) {
    }

    /**
     * Una venta que sigue sin comprobante, con los dias que lleva asi.
     *
     * Incluye las de contado a proposito: marcar "contado" dice COMO se pago, no que el recibo
     * exista. Mientras eso conto como pagado se acreditaba a gente que nunca entrego el papel.
     */
    public record Pendiente(
            Long inscripcionId,
            String entidad,
            String vendedor,
            boolean contado,
            BigDecimal totalBs,
            LocalDate fecha,
            int dias) {
    }

    /** Lo vendido en un dia, y el acumulado hasta ese dia. */
    public record Dia(
            LocalDate fecha,
            int ventas,
            int casetas,
            BigDecimal totalBs,
            BigDecimal acumuladoBs,
            int acumuladoCasetas) {
    }

    /** El cobro entero: los cortes y el detalle de lo que falta. */
    public record Cobros(List<Cobro> cortes, List<Pendiente> pendientes,
                         BigDecimal totalCobrado, BigDecimal totalPendiente) {
    }
}
