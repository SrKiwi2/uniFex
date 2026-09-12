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
        boolean conFoto) {

    /** La unica plantilla que imprime datos de la persona y por tanto exige su foto. */
    public static boolean requiereFoto(String plantilla) {
        return !"QR_GRANDE".equalsIgnoreCase(plantilla == null ? "" : plantilla.trim());
    }

    /** ¿Se puede imprimir con esa plantilla sin que quede nada pendiente? */
    public boolean apto(String plantilla) {
        if (!conComprobante) return false;
        return !requiereFoto(plantilla) || conFoto;
    }

    /** Lo que le falta para esa plantilla, en palabras, para poder decirselo a quien mira. */
    public List<String> faltantes(String plantilla) {
        return Stream.of(
                        conComprobante ? null : "sin comprobante",
                        (requiereFoto(plantilla) && !conFoto) ? "sin foto" : null)
                .filter(Objects::nonNull)
                .toList();
    }
}
