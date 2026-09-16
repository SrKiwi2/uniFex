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

    /**
     * Lo vendido por la facultad (area academica) DEL VENDEDOR que registro la venta.
     *
     * La carrera cuelga de {@code persona.id_carrera} y el area de la carrera (V35). El area
     * no se copia a ningun sitio: se deduce por la carrera, y por eso cambiar una carrera de
     * area corrige el historico entero sin tocar una sola venta.
     *
     * {@code vendedores} cuenta personas DISTINTAS que vendieron algo, no los usuarios del
     * area: una facultad con veinte vendedores de los que solo tres han vendido sale con 3, que
     * es lo que hace comparables las dos columnas de al lado.
     *
     * Las ventas de quien no tiene carrera asignada NO se descartan: se agrupan aparte. Tirarlas
     * haria que la suma del reporte no cuadrara con el total de la feria, y ese descuadre es
     * justo el que nadie sabe explicar tres meses despues.
     */
    public record Facultad(
            String sigla,
            String nombre,
            int vendedores,
            int ventas,
            int casetas,
            BigDecimal totalBs,
            /** Sobre el total vendido de la feria. 0..100. */
            BigDecimal porcentaje) {
    }

    /**
     * Lo VENDIDO de una categoria, sin lo que queda por vender.
     *
     * Es un corte de {@link Ocupacion}, no un calculo aparte: sale del mismo sitio para que las
     * dos cosas no puedan decir numeros distintos.
     */
    public record VendidoCategoria(
            String categoria,
            int vendidas,
            int total,
            BigDecimal porcentajeVendido,
            BigDecimal totalBs,
            /** Lo cobrado por esta categoria sobre el total vendido de la feria. 0..100. */
            BigDecimal porcentajeDelDinero) {
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
