package com.usic.uniFex.security;

/**
 * Expresiones de autorizacion reutilizables para {@code @PreAuthorize}.
 *
 * Los nombres NO son una convencion inventada: salen de la tabla {@code rol} de la base.
 * Los roles existentes son SUPER USUARIO, ADMINISTRADOR, ADMINISTRATIVO, CONTROL y ASESORIA.
 * Los 35 usuarios ADMINISTRATIVO son los vendedores: venden casetas, pero no rediseñan el plano.
 * (Ojo: no existe ningun rol VENDEDOR, aunque algun {@code if} viejo lo compare.)
 *
 * Las autoridades llegan como {@code ROLE_<rolNormalizado>}, ver {@link JwtUser#rolNormalizado()}.
 */
public final class Roles {

    private Roles() {
    }

    /**
     * Los mismos roles de administracion, en forma de AUTORIDAD, para las comprobaciones que
     * no se pueden hacer con {@code @PreAuthorize} porque dependen del dato.
     *
     * Caso tipico: "puedes descargar este recibo si la venta es tuya **o** si eres de
     * administracion". Eso no se expresa en una anotacion sin meterle la consulta dentro, asi
     * que se comprueba en codigo — pero leyendo los nombres de aqui, para que no acaben
     * copiados sueltos por los controladores.
     *
     * Debe coincidir con {@link #ADMINISTRA}. Si cambia uno, cambia el otro.
     */
    public static final java.util.List<String> AUTORIDADES_ADMINISTRA =
            java.util.List.of("ROLE_SUPER_USUARIO", "ROLE_ADMINISTRADOR");

    /** Solo quien puede entrar aun cuando el sistema esta en mantenimiento. */
    public static final String SOLO_SUPER_USUARIO = "hasRole('SUPER_USUARIO')";

    /**
     * Quien puede rediseñar el plano: crear categorias, mover o redimensionar casetas, bloquear.
     * Son operaciones destructivas para la venta, asi que se reservan a la administracion.
     */
    public static final String EDITA_PLANO = "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR')";

    /**
     * Quien puede gestionar los usuarios del sistema (crear logins, asignar roles, dar de baja).
     * Es la operacion mas sensible: crea credenciales de acceso. Mismos roles que el plano hoy,
     * pero se declara aparte para poder restringirla mas adelante sin tocar lo demas.
     */
    public static final String GESTIONA_USUARIOS = "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR')";

    /**
     * Administracion general: ver listados y reportes globales (todas las inscripciones, todos
     * los vendedores), a diferencia de un vendedor que solo ve lo suyo. Mismos roles hoy;
     * declarada aparte por si en el futuro un rol de solo-lectura (p.ej. ASESORIA) debe verlos.
     */
    public static final String ADMINISTRA = "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR')";

    /**
     * Quien prepara y verifica las credenciales: revisa que la inscripcion este completa
     * (comprobante y fotos) y las imprime.
     *
     * Incluye VERIFICADOR, que existe precisamente para esto y NO es administracion: no toca
     * el plano, ni los usuarios, ni las ventas. Por eso no vale reutilizar ADMINISTRA —
     * hacerlo le daria de paso los listados globales y los reportes.
     */
    public static final String VERIFICA_CREDENCIALES =
            "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR','VERIFICADOR')";

    /**
     * Quien puede USAR el modulo de credenciales, cada uno con su alcance.
     *
     * Añade ADMINISTRATIVO a los de arriba, pero **no le da lo mismo**: el vendedor solo ve y
     * imprime las credenciales de las ventas que el registro. Ese recorte no se hace aqui
     * —una expresion de rol no sabe de quien es cada venta— sino en la consulta, y se vuelve a
     * comprobar contra la base al pedir un PDF, porque los ids los manda el cliente.
     *
     * Existe separada de {@link #VERIFICA_CREDENCIALES} porque las dos cosas son distintas:
     * preparar la acreditacion de toda la feria es un trabajo, y acreditar a los propios
     * expositores es otro. Los endpoints que si son de toda la feria siguen con la primera.
     */
    /**
     * Quien puede MIRAR el listado global de inscripciones y el detalle de una venta.
     *
     * Incluye a VERIFICADOR, y sin eso el modulo quedaba a medias de una forma especialmente
     * confusa: el permiso de pantalla le daba "Inscripciones" en el menu, entraba, y el listado
     * respondia 403. Parecia que la aplicacion fallaba cuando lo que pasaba es que dos sitios
     * decian cosas distintas sobre lo mismo.
     *
     * Y tiene sentido que lo vea: acredita, y para acreditar necesita mirar la venta entera
     * —que se pago, que se adjunto, quienes son los responsables—, no solo la fila de la
     * credencial. Es mirar, no tocar: cancelar y aprobar siguen siendo de {@link #ADMINISTRA}.
     */
    /**
     * Incluye ASESORIA porque a ese rol ya se le concede la PANTALLA de inscripciones, y una
     * pantalla concedida cuyo API responde 403 es el peor fallo de los dos: el enlace aparece,
     * se toca, y no pasa nada ni se explica por que. Este proyecto ya lo sufrio con VERIFICADOR.
     *
     * Lo que da: LEER el listado y el detalle de cualquier venta, datos personales del
     * responsable incluidos (C.I. y celular). Cancelar y aprobar siguen siendo de
     * {@link #ADMINISTRA}. Si algun dia direccion no debe ver esos datos, la correccion es
     * quitarle la pantalla en `rol_pantalla` **y** sacarlo de aqui — las dos cosas, o vuelve el
     * enlace que no lleva a ninguna parte.
     */
    public static final String VE_INSCRIPCIONES =
            "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR','VERIFICADOR','ASESORIA')";

    /**
     * Quien CONSULTA los numeros de la feria: reportes y analisis de direccion.
     *
     * Incluye ASESORIA, que existe para esto y solo para esto —"ve listados y reportes de la
     * feria, sin modificar nada"—, y es el rol con el que entra quien dirige. Por eso no vale
     * reutilizar {@link #ADMINISTRA}: eso le daria de paso los usuarios, el plano y las
     * cancelaciones. Es mirar, no tocar.
     *
     * Un ADMINISTRATIVO NO entra: aqui esta el total de la feria y el ranking de sus
     * compañeros, y su sitio para eso es "Mis ventas", que solo enseña lo suyo.
     */
    public static final String VE_REPORTES =
            "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR','ASESORIA')";

    /**
     * Quien controla la puerta: escanea credenciales y anota entradas y salidas.
     *
     * CONTROL es el rol que existe para esto. Administracion entra tambien porque durante el
     * montaje suele haber una sola persona haciendo de todo, y porque quien mira los numeros
     * al final del dia necesita poder probar el circuito.
     */
    public static final String CONTROLA_ACCESO =
            "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR','CONTROL')";

    public static final String USA_CREDENCIALES =
            "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR','VERIFICADOR','ADMINISTRATIVO')";
}
