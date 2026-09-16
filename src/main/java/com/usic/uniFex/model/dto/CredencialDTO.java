package com.usic.uniFex.model.dto;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Una credencial, ya lista para imprimir o para enseñar en la vista publica del QR.
 *
 * <h2>Los requisitos dependen de la PLANTILLA</h2>
 * El comprobante de pago hace falta siempre — tambien cuando la venta se marco "al contado":
 * eso dice como se pago, no que exista el recibo. La <b>foto</b>, en cambio, solo hace falta en
 * la plantilla con etiquetas, que es la que lleva los datos de la persona impresos. La de QR
 * grande no imprime ni nombre ni C.I., asi que no tiene sentido bloquearla por una foto que no
 * se va a usar.
 *
 * Los dos requisitos van como campos sueltos y no como un simple "apto" porque quien prepara
 * las credenciales necesita saber POR QUE una no sale: "falta el comprobante" y "falta la foto"
 * se arreglan en sitios distintos y los resuelven personas distintas.
 */
public record CredencialDTO(
        Long responsableId,
        /** El que viaja en el QR. Se deriva del id y va firmado; ver CredencialCodigoService. */
        String codigo,
        String nombre,
        String ci,
        /** Ruta servida en /files/**, o null si esta persona todavia no tiene foto. */
        String fotoUrl,
        boolean esTitular,
        String entidad,
        String rubro,
        String categoria,
        /** Los numeros de caseta del expositor, ya ordenados: "14, 15, 16". */
        String casetas,
        Long inscripcionId,
        boolean conComprobante,
        boolean conFoto,
        /**
         * true si esta POR ENCIMA de los dos responsables por caseta: se le cobro aparte (V34).
         *
         * La pantalla de credenciales lo marca. Sin el distintivo, una credencial de pago se
         * mezcla con las de derecho y no hay forma de saber cual hubo que cobrar.
         */
        boolean esExtra,
        /** Bs cobrados por ese extra, congelados el dia del cobro. Null si no es extra. */
        java.math.BigDecimal montoExtra,
        /** Ruta en /files/** del comprobante de ESE cobro, o null. */
        String comprobanteExtraUrl,
        /**
         * true cuando esta venta NO cuesta nada: todas sus casetas van a 0 Bs.
         *
         * Hay categorias y casetas sueltas con precio 0 —invitados, convenios, espacios que la
         * universidad cede—. Ahi no existe ningun recibo que adjuntar, asi que exigir el
         * comprobante dejaba esas credenciales bloqueadas PARA SIEMPRE: nadie podia completar
         * un papel que nunca se emitio.
         *
         * Se manda resuelto y no el importe porque quien lee esto decide una sola cosa —si se
         * exige comprobante— y mandar el numero obligaria a cada cliente a repetir la misma
         * comparacion. El importe ya se consulta en Mis ventas y en los reportes.
         */
        boolean sinCosto) {

    /**
     * ¿Esa plantilla imprime datos de la persona y por tanto exige su foto?
     *
     * La respuesta la da el catalogo ({@link PlantillaCredencial}), no un `if` con el nombre de
     * una plantilla dentro: mientras la regla fue "todo lo que no sea QR_GRANDE", cada plantilla
     * nueva nacia exigiendo una foto que a lo mejor ni siquiera imprime.
     */
    public static boolean requiereFoto(String plantilla) {
        return PlantillaCredencial.oPorDefecto(plantilla).requiereFoto();
    }

    /**
     * ¿Hace falta el comprobante de pago para esta credencial?
     *
     * No, cuando la venta no cuesta nada: sin cobro no hay recibo que adjuntar, y pedirlo dejaba
     * esas credenciales bloqueadas para siempre.
     *
     * <b>El responsable EXTRA es la excepcion de la excepcion.</b> Un extra se cobra aparte
     * (15 Bs, V34) y su cobro NO entra en el total de la inscripcion, asi que una venta de 0 Bs
     * con un extra si tiene un cobro de por medio. Exonerar mirando solo el importe de la venta
     * habria abierto justo el agujero que la regla del comprobante viene a cerrar.
     *
     * En la practica ese caso no llega hasta aqui: `ResponsableExtraService` no crea un extra
     * sin su comprobante —el cobro y el recibo viajan en la misma peticion, para que no exista
     * el estado "extra creado y sin pagar"—. Esta condicion es el cinturon sobre el tirante: si
     * alguna vez se abriera otra via de alta, la credencial no se emitiria sin respaldo.
     */
    public boolean requiereComprobante() {
        if (!sinCosto) return true;
        return esExtra && comprobanteExtraUrl == null;
    }

    /** ¿Se puede imprimir con esa plantilla sin que quede nada pendiente? */
    public boolean apto(String plantilla) {
        if (requiereComprobante() && !conComprobante) return false;
        return !requiereFoto(plantilla) || conFoto;
    }

    /** Lo que le falta para esa plantilla, en palabras, para poder decirselo a quien mira. */
    public List<String> faltantes(String plantilla) {
        return Stream.of(
                        (requiereComprobante() && !conComprobante) ? "sin comprobante" : null,
                        (requiereFoto(plantilla) && !conFoto) ? "sin foto" : null)
                .filter(Objects::nonNull)
                .toList();
    }
}
