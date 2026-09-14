package com.usic.uniFex.controller.publico;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Lo que abre el telefono al escanear el QR de una credencial.
 *
 * <h2>El problema que resuelve</h2>
 * El QR lleva {@code <raiz>/credencial/<codigo>}, y esa direccion no existia en el servidor: la
 * SPA se sirve bajo {@code /app/}, asi que la ruta de verdad es {@code /app/credencial/<codigo>}.
 * Escanear con la camara normal del telefono abria el navegador, el servidor no reconocia la
 * ruta, la cadena web pedia sesion y acababa en un 302 a la pagina de login. El expositor veia
 * el login del sistema en vez de su credencial, y no habia forma de deducir por que.
 *
 * <h2>Por que un reenvio y no arreglar solo la raiz del QR</h2>
 * Se puede fijar {@code unifex.publico.base-url} con el {@code /app} incluido, y conviene
 * hacerlo. Pero eso solo arregla los QR que se generen A PARTIR de ahora: los ya impresos y los
 * ya entregados al telefono de un expositor seguirian sin llevar a ninguna parte, y un QR
 * entregado no se puede corregir. Con este reenvio funcionan las dos direcciones.
 *
 * Es un {@code forward} y no un {@code redirect}: la direccion no cambia en la barra, asi que
 * el enlace que alguien comparta sigue siendo el que llevaba el QR.
 */
@Controller
public class CredencialQrController {

    /**
     * {@code /credencial/**} sirve la SPA, que ya sabe pintar esta pantalla.
     *
     * El patron lleva {@code **} y no {@code {codigo}} porque la firma del codigo es base64url
     * y puede traer caracteres que partirian la ruta en dos segmentos. Aqui no hace falta leer
     * el codigo: de eso se encarga el router del navegador.
     */
    @GetMapping("/credencial/**")
    public String credencial() {
        return "forward:/app/index.html";
    }
}
