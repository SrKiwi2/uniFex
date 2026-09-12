package com.usic.uniFex.security;

import java.util.Arrays;
import java.util.List;

/**
 * Las pantallas de la aplicacion, con el nombre que se lee en el menu.
 *
 * Es el catalogo del modulo de permisos: lo que se puede marcar y desmarcar por rol. La clave
 * ({@code clave}) es la misma cadena que guarda {@code rol_pantalla.pantalla} y la misma que
 * usa la ruta de la SPA, para que las tres cosas no se puedan desincronizar.
 *
 * <h2>Lo que esto decide y lo que NO</h2>
 * Decide QUE VE cada rol: el menu y la entrada a cada ruta. <b>No autoriza nada en el
 * servidor.</b> Cada endpoint sigue protegido por {@link Roles} con {@code @PreAuthorize}, y
 * eso no se toca desde la interfaz. Desmarcar "Editor del plano" a un rol le quita el enlace;
 * lo que le impide de verdad mover una caseta es que el servidor exige EDITA_PLANO.
 *
 * Se separan a proposito: una lista editable desde una pantalla web no puede ser lo unico que
 * separe a un vendedor de rediseñar el plano.
 */
public enum PantallasSistema {

    INICIO("inicio", "Inicio", "General"),
    MAPA("mapa", "Mapa de ventas", "Venta"),
    VENTA("venta", "Registrar venta", "Venta"),
    MIS_VENTAS("mis-ventas", "Mis ventas", "Venta"),
    TABLERO("tablero", "Tablero", "Venta"),
    EDITOR("editor", "Editor del plano", "Feria"),
    VENDEDORES("vendedores", "Vendedores", "Feria"),
    INSCRIPCIONES("inscripciones", "Inscripciones", "Feria"),
    REPORTES("reportes", "Reportes", "Feria"),
    NOCHES_FEXPO("noches-fexpo", "Noches de FEXPO", "Feria"),
    CREDENCIALES("credenciales", "Credenciales", "Acreditacion"),
    ESCANER("escaner", "Escanear credencial", "Acreditacion"),
    USUARIOS("usuarios", "Usuarios", "Administracion"),
    ROLES("roles", "Roles", "Administracion"),
    PERMISOS("permisos", "Permisos por rol", "Administracion"),
    PERSONAS("personas", "Personas", "Administracion"),
    NOTIFICACIONES("notificaciones", "Notificaciones", "General");

    private final String clave;
    private final String titulo;
    /** Para agrupar el listado: con dieciseis casillas sueltas no se encuentra nada. */
    private final String grupo;

    PantallasSistema(String clave, String titulo, String grupo) {
        this.clave = clave;
        this.titulo = titulo;
        this.grupo = grupo;
    }

    public String getClave() {
        return clave;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getGrupo() {
        return grupo;
    }

    public static List<PantallasSistema> todas() {
        return Arrays.asList(values());
    }

    public static boolean existe(String clave) {
        return Arrays.stream(values()).anyMatch(p -> p.clave.equals(clave));
    }
}
