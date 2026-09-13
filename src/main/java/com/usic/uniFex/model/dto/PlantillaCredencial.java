package com.usic.uniFex.model.dto;

import java.util.List;
import java.util.Optional;

/**
 * El catalogo de plantillas de credencial: la unica lista que hay que tocar para añadir una.
 *
 * <h2>Por que existe este archivo</h2>
 * Antes cada plantilla estaba repartida en cinco sitios —las coordenadas en el generador de PDF,
 * el nombre en un {@code if}, el nombre otra vez en un {@code List.of} del controlador, la regla
 * de la foto en el DTO y la etiqueta en la pantalla— y ninguno de los cinco fallaba al compilar
 * si te saltabas uno. Olvidar el {@code if} imprimia con la plantilla equivocada; olvidar el
 * {@code List.of} dejaba todas las credenciales marcadas como "no listas" para siempre. Ahora
 * añadir una plantilla es dejar la imagen en {@code static/assets/} y escribir una constante
 * aqui: el generador, el API, los requisitos y la pantalla salen de esta lista.
 *
 * <h2>Las posiciones son FRACCIONES de la imagen (0..1)</h2>
 * Ni pixeles ni milimetros. Asi la misma disposicion vale para una plantilla de 1182 px o de
 * 4000, y para una credencial impresa de 8 cm o de 14. La {@code y} se mide desde ARRIBA, como
 * en la imagen; el PDF la invierte una sola vez, en el generador.
 *
 * Las fracciones de {@link #CON_ETIQUETAS} estan MEDIDAS sobre la plantilla, no estimadas a ojo:
 * se detectaron las cajas blancas impresas buscando las franjas de blanco puro.
 */
public record PlantillaCredencial(
        /** Lo que viaja por el API y queda escrito en `credencial_impresion.plantilla` (max 30). */
        String id,
        /** Como se llama en la pantalla. */
        String etiqueta,
        /** Media linea explicando en que se diferencia de las demas. */
        String detalle,
        /** Recurso dentro del jar, con mayusculas y minusculas EXACTAS: el servidor es Linux. */
        String imagen,
        /**
         * Si el papel imprime datos de la persona y por tanto no se emite sin su foto.
         *
         * El comprobante hace falta siempre y eso no es configurable: marcar una venta "al
         * contado" dice como se pago, no que exista el recibo.
         */
        boolean requiereFoto,
        /** Si el texto va centrado en su caja en vez de alineado a la izquierda. */
        boolean centrado,
        /** Si el texto se imprime en mayusculas, como el resto del sistema. */
        boolean mayusculas,
        /** Caja del QR; se usa el ANCHO y sale cuadrado. Es la unica que NO puede ser null. */
        Caja qr,
        Caja nombre,
        /** Entidad y rubro, juntos. */
        Caja empresa,
        Caja ci,
        /** Numeros de caseta, con la categoria debajo en letra pequeña. */
        Caja codigo) {

    /** Un rectangulo dentro de la plantilla, en fracciones de su ancho y su alto. */
    public record Caja(double x, double y, double ancho, double alto) {
    }

    /**
     * Plantilla con las etiquetas ya impresas ("NOMBRE Y APELLIDO", "EMPRESA / SERVICIO"...).
     * Los valores se dibujan en la MITAD INFERIOR de cada caja, debajo de su etiqueta.
     */
    public static final PlantillaCredencial CON_ETIQUETAS = new PlantillaCredencial(
            "CON_ETIQUETAS", "Con etiquetas", "QR arriba a la derecha",
            "static/assets/CREDENCIAL_4.jpg",
            true,    // imprime nombre y C.I.: sin foto no se emite
            false,   // alineado a la izquierda, debajo de cada etiqueta impresa
            true,
            // Centrado en el recuadro claro de arriba a la derecha, que va de 0.685 a 0.947.
            new Caja(0.703, 0.050, 0.225, 0.225),
            new Caja(0.165, 0.502, 0.758, 0.048),
            new Caja(0.165, 0.608, 0.758, 0.048),
            new Caja(0.165, 0.713, 0.431, 0.048),
            new Caja(0.658, 0.713, 0.182, 0.048));

    /**
     * Misma plantilla sin etiquetas impresas, con el QR grande y centrado un poco por encima
     * del medio. Los datos ocupan las cajas enteras, porque aqui no hay etiqueta que estorbe.
     */
    public static final PlantillaCredencial QR_GRANDE = new PlantillaCredencial(
            "QR_GRANDE", "QR grande", "QR centrado, sin etiquetas",
            "static/assets/CREDENCIAL3.jpg",
            false,   // no lleva ni nombre ni C.I.: pedir la foto solo frenaria la cola
            true,    // lo unico escrito va centrado
            true,
            // Lo mas grande que cabe entre el logotipo de FEXPO y la primera caja blanca.
            // El alto que ocupa sale de la proporcion de la plantilla, no de este numero:
            // 0.375 de ancho son 0.289 de alto, asi que termina justo encima de la caja.
            new Caja(0.3125, 0.170, 0.375, 0.375),
            null,   // sin nombre
            null,   // sin entidad ni rubro
            null,   // sin C.I.
            // Lo unico escrito, dentro de la PRIMERA caja de la plantilla y centrado: la
            // caseta grande con su categoria debajo. Quien necesite saber QUIEN es, escanea
            // el QR; el papel solo dice DE DONDE es.
            new Caja(0.147, 0.474, 0.794, 0.076));

    /**
     * Todas las plantillas que se pueden elegir, en el orden en que salen en la pantalla.
     *
     * <b>Aqui se añade una plantilla nueva, y en ningun otro sitio.</b> El generador, el
     * catalogo del API, los requisitos por plantilla y los botones de la pantalla recorren esta
     * lista.
     */
    public static final List<PlantillaCredencial> CATALOGO = List.of(CON_ETIQUETAS, QR_GRANDE);

    /** La que se usa cuando quien llama no pide ninguna. */
    public static final PlantillaCredencial POR_DEFECTO = CON_ETIQUETAS;

    /**
     * La plantilla con ese id, o vacio si no existe.
     *
     * Devuelve {@code Optional} a proposito: un id desconocido tiene que poder responderse con
     * un error. Cuando esto era un {@code if} con {@code else -> CON_ETIQUETAS}, pedir una
     * plantilla mal escrita devolvia 200 con el PDF impreso en la plantilla equivocada, y eso
     * solo se descubre mirando el papel.
     */
    public static Optional<PlantillaCredencial> de(String id) {
        String n = id == null ? "" : id.trim().toUpperCase();
        return CATALOGO.stream().filter(p -> p.id().equals(n)).findFirst();
    }

    /** La plantilla con ese id, o la de por defecto. Para lecturas, donde no hay a quien avisar. */
    public static PlantillaCredencial oPorDefecto(String id) {
        return de(id).orElse(POR_DEFECTO);
    }
}
