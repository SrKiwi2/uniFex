package com.usic.uniFex.model.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Selecciona el texto de bienvenida enviado después de registrar una venta.
 *
 * <p>Trabaja por rondas: mezcla las diez versiones y usa cada una una sola vez. Cuando
 * comienza otra ronda, evita que la primera sea igual a la última de la ronda anterior.
 * El acceso sincronizado impide repeticiones aunque se registren varias ventas a la vez.</p>
 */
@Component
public class MensajesWhatsAppVenta {

    private static final List<String> PLANTILLAS = List.of(
            String.join("\n",
                    "🎉 ¡Bienvenido a la FEXPO UAP!",
                    "", "Hola %s,", "Tu inscripción ha sido registrada con éxito.", "",
                    "📎 Te enviamos tu recibo en PDF y tus credenciales virtuales.",
                    "¡Nos vemos en la feria!"),
            String.join("\n",
                    "✅ ¡Tu participación en la FEXPO UAP está confirmada!",
                    "", "Hola %s,", "Registramos correctamente tu inscripción.", "",
                    "📎 A continuación recibirás el recibo y las credenciales virtuales.",
                    "Universidad Amazónica de Pando."),
            String.join("\n",
                    "🌟 ¡Gracias por ser parte de la FEXPO UAP!",
                    "", "Hola %s,", "Tu registro como expositor se completó correctamente.", "",
                    "📎 Adjuntamos el recibo de inscripción y las credenciales correspondientes.",
                    "Universidad Amazónica de Pando."),
            String.join("\n",
                    "🎪 ¡Ya formas parte de la FEXPO UAP!",
                    "", "Hola %s,", "Confirmamos que tu inscripción fue registrada.", "",
                    "📎 En los siguientes mensajes encontrarás tu recibo y tus credenciales virtuales.",
                    "Universidad Amazónica de Pando."),
            String.join("\n",
                    "🙌 ¡Inscripción completada con éxito!",
                    "", "Hola %s,", "Nos alegra contar con tu participación en la FEXPO UAP.", "",
                    "📎 Te compartimos el recibo en PDF y las credenciales virtuales de tu registro.",
                    "Universidad Amazónica de Pando · FEXPO UAP."),
            String.join("\n",
                    "📣 ¡Registro confirmado para la FEXPO UAP!",
                    "", "Hola %s,", "Tu inscripción ya se encuentra registrada en el sistema.", "",
                    "📎 Recibirás enseguida el recibo y las credenciales virtuales.",
                    "Universidad Amazónica de Pando."),
            String.join("\n",
                    "🎊 ¡Te damos la bienvenida a la FEXPO UAP!",
                    "", "Hola %s,", "El registro de tu participación concluyó satisfactoriamente.", "",
                    "📎 Encontrarás adjuntos tu recibo y las credenciales virtuales.",
                    "Universidad Amazónica de Pando."),
            String.join("\n",
                    "💫 ¡Todo listo para tu participación!",
                    "", "Hola %s,", "Tu inscripción a la FEXPO UAP fue procesada correctamente.", "",
                    "📎 Te enviaremos el recibo de compra y las credenciales virtuales.",
                    "Universidad Amazónica de Pando."),
            String.join("\n",
                    "🤝 ¡Gracias por sumarte a la FEXPO UAP!",
                    "", "Hola %s,", "Confirmamos el registro exitoso de tu inscripción.", "",
                    "📎 En este chat recibirás el recibo en PDF y tus credenciales virtuales.",
                    "Universidad Amazónica de Pando."),
            String.join("\n",
                    "🏢 ¡Tu espacio en la FEXPO UAP está registrado!",
                    "", "Hola %s,", "Completamos correctamente tu proceso de inscripción.", "",
                    "📎 A continuación te compartimos el recibo y las credenciales virtuales.",
                    "¡Te esperamos!"));

    private static final String CIERRE = String.join("\n",
            "Responde este mensaje para confirmar la entrega.",
            "Registra este número para estar al tanto de todas las novedades de la FEXPO.");

    private final SecureRandom aleatorio = new SecureRandom();
    private final List<Integer> pendientes = new ArrayList<>();
    private Integer ultima;

    public synchronized String siguiente(String nombreEntidad) {
        if (pendientes.isEmpty()) prepararRonda();
        int indice = pendientes.removeLast();
        ultima = indice;
        String nombre = nombreEntidad == null || nombreEntidad.isBlank() ? "expositor" : nombreEntidad.trim();
        return PLANTILLAS.get(indice).formatted(nombre) + "\n\n" + CIERRE;
    }

    private void prepararRonda() {
        for (int i = 0; i < PLANTILLAS.size(); i++) pendientes.add(i);
        Collections.shuffle(pendientes, aleatorio);
        if (ultima != null && pendientes.getLast().equals(ultima)) {
            Collections.swap(pendientes, pendientes.size() - 1, pendientes.size() - 2);
        }
    }

    static int cantidadVariantes() {
        return PLANTILLAS.size();
    }
}
