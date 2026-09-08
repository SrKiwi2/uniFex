package com.usic.uniFex.model.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IInscripcionDao;
import com.usic.uniFex.model.entity.Inscripcion;

import lombok.RequiredArgsConstructor;

/**
 * El identificador verificable de la nota de venta.
 *
 * Vive aparte del PDF a proposito: verificar una nota presentada en papel no deberia obligar
 * a construir un documento, y quien verifica (alguien en la puerta con un telefono) no es
 * quien emite.
 *
 * <h2>Por que el codigo anterior no servia</h2>
 *
 * El recibo traia un "codigo de autenticacion" que era {@code SHA-256(id | NIT | fecha)}
 * recortado. Tenia tres problemas que lo dejaban en adorno:
 *
 * <ol>
 *   <li>La fecha era {@code new Date()}, el instante de imprimir. Cada reimpresion daba un
 *       codigo <b>distinto</b>: dos copias de la misma venta no coincidian.</li>
 *   <li>Los tres ingredientes van impresos en el propio papel. Cualquiera con la formula
 *       podia recalcular el hash y fabricar una nota que "cuadra" sola.</li>
 *   <li>No se guardaba, asi que no habia contra que comprobarlo.</li>
 * </ol>
 *
 * <h2>Que se hace ahora</h2>
 *
 * El codigo se firma con <b>HMAC-SHA256 y el secreto del servidor</b>: sin ese secreto no se
 * puede fabricar uno valido aunque se conozcan todos los datos impresos. Se emite <b>una
 * vez</b>, se guarda (V14) y desde entonces la nota siempre sale igual. Y como esta guardado,
 * verificar es buscarlo: si no existe, el papel es falso.
 *
 * El codigo por si solo no prueba que la venta siga vigente — una venta cancelada conserva su
 * codigo. Por eso {@link #verificar(String)} devuelve tambien el estado, y quien controla en
 * la puerta ve "ANULADA" en vez de un "valido" enganioso.
 */
@Service
@RequiredArgsConstructor
public class NotaVentaCodigoService {

    private final IInscripcionDao inscripcionDao;

    /**
     * Se reutiliza el secreto del JWT: ya existe, ya viene de una variable de entorno y ya se
     * trata como secreto en el despliegue. Si algun dia se rota, los codigos ya emitidos
     * siguen siendo validos porque estan GUARDADOS — no se recalculan al verificar.
     */
    @Value("${unifex.jwt.secret}")
    private String secreto;

    private static final String PREFIJO = "FEX";

    /** Lo que se sabe de una nota al verificarla. `existe=false` = el papel no corresponde a ninguna venta. */
    public record Verificacion(
            boolean existe,
            boolean vigente,
            Long inscripcionId,
            String entidad,
            String emitida,
            String estado) {

        public static Verificacion inexistente() {
            return new Verificacion(false, false, null, null, null, "NO ENCONTRADA");
        }
    }

    /**
     * Devuelve el codigo de la venta, emitiendolo la primera vez.
     *
     * {@code REQUIRES_NEW} porque el generador de PDF corre en una transaccion de solo
     * lectura: sin esto, emitir el codigo durante la primera impresion no se podria guardar.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String obtenerOEmitir(Long inscripcionId) {
        Inscripcion ins = inscripcionDao.findById(inscripcionId).orElse(null);
        if (ins == null) return null;
        if (ins.getNotaCodigo() != null) return ins.getNotaCodigo();

        LocalDateTime emitida = LocalDateTime.now();
        String codigo = construir(ins.getId(), emitida);
        ins.setNotaCodigo(codigo);
        ins.setNotaEmitidaEn(emitida);
        inscripcionDao.save(ins);
        return codigo;
    }

    /** Comprueba un codigo leido del papel o del QR. Publico: lo usa quien controla en la puerta. */
    @Transactional(readOnly = true)
    public Verificacion verificar(String codigo) {
        if (codigo == null || codigo.isBlank()) return Verificacion.inexistente();
        Inscripcion ins = inscripcionDao.findByNotaCodigo(codigo.trim().toUpperCase()).orElse(null);
        if (ins == null) return Verificacion.inexistente();

        // 'X' es la baja logica: la venta fue cancelada (V10). El codigo sigue existiendo,
        // pero la nota ya no ampara nada y hay que decirlo.
        boolean anulada = "X".equalsIgnoreCase(ins.getEstado());
        return new Verificacion(
                true,
                !anulada,
                ins.getId(),
                ins.getEntidad() != null ? ins.getEntidad().getNombre() : null,
                ins.getNotaEmitidaEn() != null ? ins.getNotaEmitidaEn().toString() : null,
                anulada ? "ANULADA" : "VIGENTE");
    }

    /**
     * {@code FEX-<id>-<12 hex del HMAC>}. Lleva el id a la vista para poder buscarlo a mano si
     * hace falta, y la firma es lo que impide inventarse uno.
     */
    private String construir(Long id, LocalDateTime emitida) {
        String base = id + "|" + emitida;
        return PREFIJO + "-" + id + "-" + hmacHex(base).substring(0, 12).toUpperCase();
    }

    private String hmacHex(String mensaje) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el codigo de la nota", e);
        }
    }
}
