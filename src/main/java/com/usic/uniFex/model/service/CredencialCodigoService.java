package com.usic.uniFex.model.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * El codigo que viaja en el QR de una credencial.
 *
 * <h2>Que es</h2>
 * {@code FXC-<id del responsable en base36>-<firma>}, por ejemplo {@code FXC-2P-h7Kd9wQ1xR2a}.
 * Lleva el id a la vista y una firma HMAC-SHA256 hecha con el secreto del servidor.
 *
 * <h2>Por que NO se guarda en una tabla</h2>
 * Porque no hace falta. El codigo se DERIVA del id del responsable, asi que:
 * <ul>
 *   <li>generar una credencial no escribe en la base — se puede reimprimir mil veces y sale
 *       el mismo codigo, que es justo lo que se quiere de algo ya impreso y entregado;</li>
 *   <li>validar un codigo no consulta la base para saber si es autentico: se recalcula la
 *       firma. En la puerta de la feria, con mala cobertura, eso importa;</li>
 *   <li>no hay estado que se pueda desincronizar entre el papel y el sistema.</li>
 * </ul>
 *
 * El precio es que <b>no se puede revocar un codigo suelto</b>: quien tenga la credencial
 * impresa tiene un codigo valido mientras exista ese responsable. Si algun dia hace falta
 * revocar (una credencial perdida), el cambio es añadir una tabla con los codigos anulados y
 * consultarla aqui; el formato del codigo no cambia y lo ya impreso sigue sirviendo.
 *
 * <h2>Por que se puede leer el id</h2>
 * A proposito: el id de un responsable no es un secreto, y llevarlo dentro evita una consulta
 * por indice para encontrarlo. Lo que no se puede es FABRICAR un codigo: sin el secreto, la
 * firma no sale, y cambiar el id invalida la firma.
 */
@Service
@Slf4j
public class CredencialCodigoService {

    /**
     * Se reutiliza el secreto del JWT, igual que hace {@link NotaVentaCodigoService}: ya existe,
     * ya viene de una variable de entorno y ya se trata como secreto en el despliegue.
     *
     * Ojo: si se rota, los codigos ya impresos dejan de validar. Es el mismo compromiso que la
     * nota de venta y esta asumido.
     */
    @Value("${unifex.jwt.secret}")
    private String secreto;

    private static final String PREFIJO = "FXC";
    /**
     * 12 caracteres base64url = 72 bits. De sobra para que no se adivine a fuerza bruta.
     *
     * El alfabeto base64url incluye {@code -} y {@code _}, asi que la firma puede llevar
     * guiones: quien lea un codigo tiene que separarlo por los DOS primeros guiones, nunca por
     * todos. Ver {@link #responsableDe(String)}.
     */
    private static final int LARGO_FIRMA = 12;

    /** El codigo publico de un responsable. Siempre el mismo para el mismo responsable. */
    public String codigoDe(Long responsableId) {
        if (responsableId == null) return null;
        String id = Long.toString(responsableId, 36).toUpperCase();
        return PREFIJO + "-" + id + "-" + firmar(id);
    }

    /**
     * Comprueba un codigo leido de un QR y devuelve el responsable al que pertenece.
     *
     * @return el id del responsable, o {@code null} si el codigo no es autentico.
     */
    public Long responsableDe(String codigo) {
        if (codigo == null) return null;
        // El limite de 3 no es cosmetico: la firma es base64url, cuyo alfabeto INCLUYE el
        // guion. Con un split normal, un codigo cuya firma llevara un guion se partia en
        // cuatro y se rechazaba — alrededor de una credencial de cada seis quedaba imposible
        // de validar en la puerta, con el papel bien impreso en la mano. Con limite 3 el
        // ultimo trozo se queda con todo lo que venga detras del segundo guion, que es
        // exactamente la firma. El id es base36, asi que nunca lleva guiones.
        String[] partes = codigo.trim().toUpperCase().split("-", 3);
        if (partes.length != 3 || !PREFIJO.equals(partes[0])) return null;

        String id = partes[1];
        // Comparacion en tiempo constante: comparar firmas con equals() filtra por el primer
        // caracter que difiere y, medido muchas veces, deja adivinar la firma byte a byte.
        if (!MessageDigest.isEqual(
                firmar(id).getBytes(StandardCharsets.UTF_8),
                partes[2].getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        try {
            return Long.parseLong(id, 36);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** La URL que se mete en el QR. Es lo que se abre al escanearlo. */
    public String urlPublica(String base, String codigo) {
        String raiz = (base == null || base.isBlank()) ? "" : base.replaceAll("/+$", "");
        return raiz + "/credencial/" + codigo;
    }

    private String firmar(String id) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            // El prefijo "CRED|" separa este uso del secreto de cualquier otro: dos cosas
            // distintas firmadas con la misma clave nunca deben poder confundirse.
            byte[] h = mac.doFinal(("CRED|" + id).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h)
                    .substring(0, LARGO_FIRMA).toUpperCase();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el codigo de credencial", e);
        }
    }
}
