package com.usic.uniFex.controller.admin;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.uniFex.anotacion.ValidarUsuarioAutenticado;
import com.usic.uniFex.model.IService.IUsuarioService;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Responsable;
import com.usic.uniFex.model.entity.Usuario;

import ch.qos.logback.core.model.Model;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AdminController {

    private final IUsuarioService IusuarioService;
    private final PasswordEncoder passwordEncoder;
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @ValidarUsuarioAutenticado
    @GetMapping(value = "/admin")
    public String adminIndex(HttpServletRequest request, Model model) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        logger.info("Usuario en sesión: {}", usuario.getPersona().getNombre());

        Persona persona = usuario.getPersona();

        request.getSession().setAttribute("persona", persona);
        return "inicio-admin";
    }

    @PostMapping("/iniciar-sesion")
    public ResponseEntity<String> iniciarSesion(
        @RequestParam String usuario,
        @RequestParam String contrasena, 
        HttpServletRequest request, RedirectAttributes flash) {

        // los dos parametros de usuario, contraseña vienen del formulario html
        Usuario usuario_ = IusuarioService.findByUsername(usuario).orElse(null);
        if (usuario_ == null || !passwordEncoder.matches(contrasena, usuario_.getPassword())) {
            return ResponseEntity.ok("Usuario o contraseña incorrectos!");
        }

        if ("INACTIVO".equals(usuario_.getEstado())) {
            return ResponseEntity.ok("Este usuario esta en estado inactivo!");
        }

            HttpSession session = request.getSession(true);
            session.setAttribute("usuario", usuario_);
            session.setAttribute("persona", usuario_.getPersona());

            String rol = (usuario_.getRol() != null && usuario_.getRol().getNombre() != null)
                    ? usuario_.getRol().getNombre().toUpperCase()
                    : "";

            session.setAttribute("nombre_rol", usuario_.getRol().getNombre());
            flash.addAttribute("success", usuario_.getPersona().getNombre());

            // 4) Respuesta según rol
            String respuesta = "RESPONSABLE".equals(rol) ? "Inicio Responsable" : "Iniciando Session";
            return ResponseEntity.ok(respuesta);
    }

    @ValidarUsuarioAutenticado
    @RequestMapping("/cerrar_sesion")
    public String cerrarSesion(HttpServletRequest request, RedirectAttributes flash) {
        Usuario usuarioLogueado = (Usuario) request.getSession().getAttribute("usuario");
        HttpSession sessionAdministrador = request.getSession();
        if (sessionAdministrador != null) {
            sessionAdministrador.invalidate();
            flash.addAttribute("validado", "Se cerro sesion con exito");
            logger.info("Usuario cerro sesión: {}", usuarioLogueado.getPersona().getNombre());
        }
        return "redirect:/";
    }
}
