package com.usic.uniFex.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * Los roles que el sistema da por sentados. Es la unica fuente de verdad de la tabla {@code rol}:
 * el arranque los siembra desde aqui (ver UniFexApplication) y el modulo de roles los marca como
 * "del sistema" para que nadie los renombre ni los borre.
 *
 * Por que estan protegidos: los nombres viajan literales por tres sitios que el compilador no
 * relaciona entre si — las expresiones de {@link Roles} ({@code hasAnyRole('SUPER_USUARIO',...)}),
 * la consulta {@code IUsuarioDao.idsDeAdministracion()} y el {@code if} por rol de AdminController.
 * Renombrar "SUPER USUARIO" desde la interfaz no daria ningun error: simplemente dejaria a la
 * administracion sin permisos y sin forma de recuperarlos, porque el propio modulo que arregla
 * los roles exige ese rol para entrar.
 *
 * El nombre que se guarda lleva espacio ("SUPER USUARIO"); la autoridad de Spring lo lleva con
 * guion bajo. Esa conversion vive en un solo sitio: {@link JwtUser#rolNormalizado()}.
 */
public enum RolesSistema {

    SUPER_USUARIO("SUPER USUARIO",
            "Control total: gestiona usuarios, roles, el plano de la feria y toda la administracion."),

    ADMINISTRADOR("ADMINISTRADOR",
            "Administracion de la feria: usuarios, plano, inscripciones y reportes globales."),

    ADMINISTRATIVO("ADMINISTRATIVO",
            "Vendedor: reserva, vende y consulta sus propias ventas. No redisena el plano."),

    CONTROL("CONTROL",
            "Control de acceso en puerta: verifica credenciales y registra el ingreso de responsables."),

    ASESORIA("ASESORIA",
            "Consulta: ve listados y reportes de la feria, sin modificar nada."),

    VERIFICADOR("VERIFICADOR",
            "Verifica que la inscripcion este completa (comprobante y fotos) antes de imprimir credenciales.");

    private final String nombre;
    private final String descripcion;

    RolesSistema(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Busca por el nombre tal como se guarda, ignorando mayusculas y espacios sobrantes. */
    public static Optional<RolesSistema> porNombre(String nombre) {
        String buscado = normalizar(nombre);
        return Arrays.stream(values())
                .filter(r -> r.nombre.equals(buscado))
                .findFirst();
    }

    /** true si ese nombre corresponde a un rol del sistema (no se renombra ni se elimina). */
    public static boolean esDelSistema(String nombre) {
        return porNombre(nombre).isPresent();
    }

    /**
     * Forma canonica de un nombre de rol: sin espacios en los extremos, sin espacios dobles y en
     * MAYUSCULAS. Todos los roles se guardan asi, para que "Administrador" y "ADMINISTRADOR" no
     * acaben siendo dos filas distintas que conceden permisos distintos.
     */
    public static String normalizar(String nombre) {
        return nombre == null ? "" : nombre.trim().replaceAll("\\s{2,}", " ").toUpperCase(java.util.Locale.ROOT);
    }
}
