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
 * El codigo que viaja en el QR de una credencial de personal de apoyo.
 *
 * {@code FXA-<id de la ficha en base36>-<firma>}, espejo del {@code FXC} de expositores
 * (ver CredencialCodigoService: mismas garantias —derivado del id, sin tabla, firmala
 * HMAC—, pero con otro prefijo y otro dominio de firma para que un codigo de apoyo no
 * pueda validarse nunca como uno de expositor ni al reves).
 */
@Service
@Slf4j
public class ApoyoCodigoService {

    /** Se reutiliza el secreto del JWT: ya existe y ya se trata como secreto. */
    @Value("${unifex.jwt.secret}")
    private String secreto;

    private static final String PREFIJO = "FXA";
    private static final int LARGO_FIRMA = 12;

    /** El codigo publico de una ficha de apoyo. Siempre el mismo para la misma ficha. */
    public String codigoDe(Long apoyoId) {
        if (apoyoId == null) return null;
        String id = Long.toString(apoyoId, 36).toUpperCase();
        return PREFIJO + "-" + id + "-" + firmar(id);
    }

    /** Comprueba un codigo leido de un QR y devuelve la ficha a la que pertenece. */
    public Long apoyoDe(String codigo) {
        if (codigo == null) return null;
        // Limite 3 igual que en FXC: la firma base64url puede llevar guiones.
        String[] partes = codigo.trim().toUpperCase().split("-", 3);
        if (partes.length != 3 || !PREFIJO.equals(partes[0])) return null;

        String id = partes[1];
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
            // Dominio propio: un FXC nunca valida como FXA aunque compartan secreto.
            byte[] h = mac.doFinal(("APOYO|" + id).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h)
                    .substring(0, LARGO_FIRMA).toUpperCase();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el codigo de apoyo", e);
        }
    }
}
