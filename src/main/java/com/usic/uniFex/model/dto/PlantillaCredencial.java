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
        /** Numeros de caseta. Con `zona` a null, lleva la categoria debajo en letra pequeña. */
        Caja codigo,
        /**
         * Caja aparte para la categoria, cuando la plantilla la tiene impresa como "ZONA"
         * junto al codigo de puesto. A null, la categoria va debajo del codigo.
         */
        Caja zona,
        /**
         * Donde va la foto del responsable, si la plantilla la lleva. A null no se dibuja.
         *
         * En la credencial virtual la foto es REDONDA, y eso no se declara aqui: la caja es el
         * cuadrado que la contiene y el generador la recorta en circulo. Declarar la forma
         * seria un campo mas que solo usa una plantilla.
         */
        Caja foto) {

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
            new Caja(0.658, 0.713, 0.182, 0.048),
            null,   // la categoria va debajo del codigo, no en caja propia
            null);  // el papel no lleva foto

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
            new Caja(0.147, 0.474, 0.794, 0.076),
            null,
            null);

    /**
     * Todas las plantillas que se pueden elegir, en el orden en que salen en la pantalla.
     *
     * <b>Aqui se añade una plantilla nueva, y en ningun otro sitio.</b> El generador, el
     * catalogo del API, los requisitos por plantilla y los botones de la pantalla recorren esta
     * lista.
     */
    /**
     * La CREDENCIAL VIRTUAL: la que se entrega de verdad.
     *
     * No esta pensada para papel sino para la pantalla del telefono del expositor, que es como
     * se reparte y como se enseña en la puerta. Por eso es vertical (900x1600, proporcion de
     * movil) y por eso se genera como IMAGEN y no como PDF sobre hoja carta: un PDF de carta en
     * un telefono sale diminuto y hay que ampliarlo justo cuando hay cola.
     *
     * Las cajas estan MEDIDAS sobre la plantilla, detectando sus recuadros blancos y el marco
     * punteado del QR, igual que se hizo con las otras.
     */
    public static final PlantillaCredencial CREDENCIAL_VIRTUAL = new PlantillaCredencial(
            "CREDENCIAL_VIRTUAL", "Credencial virtual", "Para el teléfono, con foto y QR",
            "static/assets/credencialvirtual.jpeg",
            true,    // lleva la foto de la persona: sin ella no se emite
            false,   // los valores van alineados a la izquierda, bajo su etiqueta impresa
            true,
            // Dentro del marco punteado de arriba a la derecha (x 0.489..0.958, y 0.020..0.285),
            // con holgura para que el QR no toque el borde: un QR pegado al marco confunde a
            // algunos lectores.
            new Caja(0.512, 0.034, 0.415, 0.415),
            new Caja(0.1422, 0.5119, 0.808, 0.053),   // NOMBRE COMPLETO
            new Caja(0.1422, 0.6006, 0.809, 0.054),   // EMPRESA / SERVICIO
            new Caja(0.1422, 0.6900, 0.808, 0.054),   // # CEDULA DE IDENTIDAD
            new Caja(0.1433, 0.7794, 0.311, 0.054),   // COD. PUESTO
            new Caja(0.4800, 0.7794, 0.471, 0.054),   // ZONA (la categoria)
            // El hueco circular de la izquierda: centro (0.345, 0.360), diametro 0.366 del ancho.
            new Caja(0.1622, 0.2569, 0.3656, 0.2056));

    /**
     * Todas las plantillas que se pueden elegir, en el orden en que salen en la pantalla.
     *
     * <b>Aqui se añade una plantilla nueva, y en ningun otro sitio.</b> El generador, el
     * catalogo del API, los requisitos por plantilla y los botones de la pantalla recorren esta
     * lista. La virtual va primera porque es la que se usa a diario.
     */
    public static final List<PlantillaCredencial> CATALOGO =
            List.of(CREDENCIAL_VIRTUAL, CON_ETIQUETAS, QR_GRANDE);

    /** La que se usa cuando quien llama no pide ninguna. */
    public static final PlantillaCredencial POR_DEFECTO = CREDENCIAL_VIRTUAL;

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
