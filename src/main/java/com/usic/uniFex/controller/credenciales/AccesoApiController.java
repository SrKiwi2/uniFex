package com.usic.uniFex.controller.credenciales;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dao.CredencialAccesoDao;
import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.service.CredencialCodigoService;
import com.usic.uniFex.model.service.CredencialService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Control de entradas y salidas en la puerta.
 *
 * <h2>El sentido lo elige quien escanea</h2>
 * No se alterna solo. Alternar parece mas comodo —el primer escaneo entrada, el segundo
 * salida— hasta que alguien escanea dos veces seguidas por nerviosismo o porque el telefono no
 * respondio a la primera: a partir de ahi TODO queda invertido, y nadie se entera hasta que
 * alguien mira los numeros al final del dia. Con el sentido explicito, un escaneo de mas es un
 * movimiento de mas y se arregla registrando el contrario.
 *
 * <h2>Registrar exige sesion; mirar la credencial no</h2>
 * La vista publica del QR sigue abierta, porque la abre cualquiera con la camara de su
 * telefono. Anotar un movimiento es otra cosa: es un acto de control, queda firmado con quien
 * lo hizo, y solo lo hace quien esta en la puerta.
 */
@RestController
@RequestMapping("/api/app/accesos")
@RequiredArgsConstructor
@Slf4j
public class AccesoApiController {

    private final CredencialCodigoService codigos;
    private final CredencialService credenciales;
    private final CredencialAccesoDao accesos;

    /** `codigo` es el del QR; `sentido` es "E" o "S". */
    public record Movimiento(String codigo, String sentido) {}

    @PostMapping
    @PreAuthorize(Roles.CONTROLA_ACCESO)
    public ResponseEntity<Map<String, Object>> registrar(
            @RequestBody Movimiento req,
            @RequestHeader(value = "X-Origen", required = false) String origen) {

        String sentido = req == null || req.sentido() == null ? "" : req.sentido().trim().toUpperCase();
        if (!CredencialAccesoDao.ENTRADA.equals(sentido) && !CredencialAccesoDao.SALIDA.equals(sentido)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false, "mensaje", "Hay que decir si es entrada o salida"));
        }

        // Se valida la FIRMA antes de tocar la base: un codigo inventado no llega a consultar
        // nada, igual que en la vista publica.
        Long responsableId = codigos.responsableDe(req.codigo());
        CredencialDTO c = responsableId == null
                ? null : credenciales.porResponsable(responsableId).orElse(null);
        if (c == null) {
            log.info("Acceso rechazado, codigo no valido: {}", req == null ? null : req.codigo());
            return ResponseEntity.status(404).body(Map.of(
                    "ok", false, "valida", false, "mensaje", "Esta credencial no es válida"));
        }

        /*
         * Se avisa si el movimiento repite el anterior —dos entradas seguidas, dos salidas—
         * pero NO se rechaza. En la puerta puede pasar por mil motivos razonables: alguien
         * salio por otra puerta sin escanear, o el control cambio de turno. Bloquearlo dejaria
         * a una persona fuera por un fallo de registro; avisarlo deja que quien esta ahi
         * decida, que es quien esta viendo lo que pasa.
         */
        String previo = accesos.ultimoSentido(c.responsableId());
        boolean repetido = sentido.equals(previo);

        accesos.registrar(c.responsableId(), sentido, usuarioActual(),
                "APK".equalsIgnoreCase(origen) ? "APK" : "WEB");

        int[] conteo = accesos.conteo(c.responsableId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("valida", true);
        m.put("sentido", sentido);
        m.put("repetido", repetido);
        m.put("nombre", c.nombre());
        m.put("ci", c.ci());
        m.put("fotoUrl", c.fotoUrl());
        m.put("entidad", c.entidad());
        m.put("categoria", c.categoria());
        m.put("casetas", c.casetas());
        m.put("entradas", conteo[0]);
        m.put("salidas", conteo[1]);
        m.put("dentro", CredencialAccesoDao.ENTRADA.equals(sentido));
        m.put("historial", historial(c.responsableId()));
        return ResponseEntity.ok(m);
    }

    /** Cuanta gente hay dentro ahora mismo: el numero que importa en una evacuacion. */
    @GetMapping("/dentro")
    @PreAuthorize(Roles.CONTROLA_ACCESO)
    public Map<String, Object> dentro() {
        return Map.of("dentro", accesos.dentroAhora());
    }

    private List<Map<String, Object>> historial(Long responsableId) {
        return accesos.historial(responsableId, 8).stream().map(f -> {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("sentido", f[0]);
            h.put("cuando", String.valueOf(f[1]));
            h.put("usuario", f[2]);
            h.put("origen", f[3]);
            return h;
        }).toList();
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }
}
