package com.usic.uniFex.model.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Quien esta usando la aplicacion AHORA MISMO.
 *
 * Vive **solo en memoria**, a proposito. Esto responde "¿quien esta conectado en este momento?",
 * que es una pregunta del presente: guardarlo en la base significaria una fila por latido —miles
 * al dia— para contestar algo que dentro de un minuto ya no vale. Si algun dia hace falta el
 * historial ("¿quien estuvo conectado el martes?"), eso es otra cosa y necesita su propia tabla.
 *
 * Consecuencia que hay que tener presente: **al reiniciar el servidor la lista se vacia** y se
 * vuelve a llenar sola con el siguiente latido de cada cliente, en menos de un minuto. No es un
 * fallo, es el precio de no escribir en la base.
 *
 * Tampoco hay evento de "se fue": cerrar la aplicacion de golpe, quedarse sin bateria o perder la
 * señal no avisan de nada. Por eso la ausencia se deduce del SILENCIO: quien no late en
 * {@link #VENTANA_VIVO_S} segundos deja de contar como conectado. Es la unica forma fiable.
 */
@Service
public class PresenciaService {

    /** Sin latido en este tiempo, se deja de considerar conectado. */
    public static final long VENTANA_VIVO_S = 75;
    /** Pasado esto, la entrada se olvida del todo para no acumular usuarios de ayer. */
    private static final long VENTANA_OLVIDO_S = 15 * 60;

    /** Lo que se sabe de un usuario conectado. */
    public record Presencia(Long usuarioId, String usuario, String nombre, String rol,
                            String pantalla, String titulo, Instant visto, String origen) {
    }

    private final Map<Long, Presencia> vistos = new ConcurrentHashMap<>();

    /**
     * Anota un latido. Lo llama el cliente cada pocos segundos y al cambiar de pantalla.
     *
     * Se guarda la pantalla que DICE el cliente, y eso basta: es informacion para mirar, no una
     * decision de permisos. Lo que no se cree bajo palabra es si esta vendiendo algo — eso se
     * mira en la base, que es donde estan las casetas reservadas de verdad.
     */
    public void latir(Long usuarioId, String usuario, String nombre, String rol,
                      String pantalla, String titulo, String origen) {
        if (usuarioId == null) return;
        vistos.put(usuarioId, new Presencia(usuarioId, usuario, nombre, rol,
                pantalla, titulo, Instant.now(), origen));
    }

    /** Se fue por la puerta: cerro sesion. Distinto de quedarse callado. */
    public void olvidar(Long usuarioId) {
        if (usuarioId != null) vistos.remove(usuarioId);
    }

    /**
     * Los vistos recientemente, el mas activo primero.
     *
     * Devuelve tambien a los que llevan un rato callados —hasta {@link #VENTANA_OLVIDO_S}— con su
     * marca de tiempo: "Ana, sin actividad hace 4 minutos" dice mas que hacerla desaparecer,
     * sobre todo cuando lo que se quiere saber es si alguien dejo una venta a medias.
     */
    public List<Presencia> listar() {
        Instant limite = Instant.now().minusSeconds(VENTANA_OLVIDO_S);
        vistos.values().removeIf(p -> p.visto().isBefore(limite));
        return vistos.values().stream()
                .sorted(Comparator.comparing(Presencia::visto).reversed())
                .toList();
    }

    public static boolean estaVivo(Presencia p) {
        return p.visto().isAfter(Instant.now().minusSeconds(VENTANA_VIVO_S));
    }
}
