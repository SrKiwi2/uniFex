package com.usic.uniFex.Config;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.usic.uniFex.anotacion.ValidarUsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Hace que la anotacion {@link ValidarUsuarioAutenticado} finalmente proteja algo:
 * si un metodo de controlador la lleva y no hay usuario en sesion, redirige al
 * inicio (login) en vez de dejar que el controlador falle con NullPointerException.
 *
 * Solo afecta al sitio web (sesion). El API movil usa JWT (otra cadena de seguridad).
 */
@Component
public class AutenticacionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (handler instanceof HandlerMethod hm
                && hm.getMethodAnnotation(ValidarUsuarioAutenticado.class) != null) {
            Object usuario = request.getSession().getAttribute("usuario");
            if (usuario == null) {
                response.sendRedirect(request.getContextPath() + "/");
                return false;
            }
        }
        return true;
    }
}
