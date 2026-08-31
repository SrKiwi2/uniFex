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
}
