package com.usic.uniFex.security;

/** Identidad autenticada extraida de un JWT; queda como principal en el SecurityContext. */
public record JwtUser(Long id, String username, String rol) {

    /**
     * El rol tal como lo espera Spring Security detras del prefijo {@code ROLE_}.
     *
     * Los roles reales de la tabla {@code rol} son SUPER USUARIO, ADMINISTRADOR,
     * ADMINISTRATIVO, CONTROL y ASESORIA. "SUPER USUARIO" lleva un espacio, y una autoridad
     * con espacio no se puede nombrar en una expresion {@code hasRole(...)}, asi que aqui se
     * normaliza a SUPER_USUARIO. Es el unico punto donde se hace esa conversion.
     */
    public String rolNormalizado() {
        return (rol == null) ? "" : rol.toUpperCase().replace(' ', '_');
    }
}
