package com.usic.uniFex.controller.usuario;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.usic.uniFex.Config.Encriptar;
import com.usic.uniFex.anotacion.ValidarUsuarioAutenticado;
import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IRolService;
import com.usic.uniFex.model.IService.IUsuarioService;
import com.usic.uniFex.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/usuario")
@RequiredArgsConstructor
public class UsuarioController {

    private final PasswordEncoder passwordEncoder;
    
    private final IUsuarioService usuarioService;
    private final IPersonaService personaService;
    private final IRolService rolService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio() {
        return "usuario/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model) throws Exception {

        List<Usuario> listaUsuarios = usuarioService.findAll();
        List<String> encryptedIds = new ArrayList<>();
        for (Usuario usuarios : listaUsuarios) {
            String id_encryptado = Encriptar.encrypt(Long.toString(usuarios.getId()));
            encryptedIds.add(id_encryptado);
        }
        model.addAttribute("listaUsuarios", listaUsuarios);
        model.addAttribute("id_encryptado", encryptedIds);

        return "usuario/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario(Model model, Usuario usuario) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("listaPersonas", personaService.listarPersonas());
        model.addAttribute("listaRoles", rolService.findAll());

        return "usuario/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_usuario}")
    public String formularioEdit(Model model, @PathVariable("id_usuario") String idUsuario) throws Exception {

        Long id = Long.parseLong(Encriptar.decrypt(idUsuario));
        model.addAttribute("usuario", usuarioService.findById(id));
        model.addAttribute("listaPersonas", personaService.listarPersonas());
        model.addAttribute("listaRoles", rolService.findAll());
        model.addAttribute("edit", "true");

        return "usuario/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-usuario")
    public ResponseEntity<String> registrarUsuario(
            HttpServletRequest request,
            @RequestParam("username") String username,
            @RequestParam("password") String rawPassword,
            @RequestParam("persona") Long personaId,
            @RequestParam("rol") Long rolId) {

        // Normalizar sin re-asignar la variable usada en el lambda
        final String usernameNorm = (username == null) ? null : username.trim();

        // 1) Validaciones básicas
        if (usernameNorm == null || usernameNorm.isBlank())
            return ResponseEntity.badRequest().body("El nombre de usuario es obligatorio.");
        if (rawPassword == null || rawPassword.isBlank())
            return ResponseEntity.badRequest().body("La contraseña es obligatoria.");
        if (personaId == null)
            return ResponseEntity.badRequest().body("Debe seleccionar una persona.");
        if (rolId == null)
            return ResponseEntity.badRequest().body("Debe seleccionar un rol.");

        // 2) Reglas de negocio
        boolean usernameExiste = usuarioService.findAll().stream()
                .anyMatch(u -> u.getUsername() != null && u.getUsername().equalsIgnoreCase(usernameNorm));
        if (usernameExiste)
            return ResponseEntity.badRequest().body("El nombre de usuario ya está en uso.");

        boolean personaYaTieneUsuario = usuarioService.findAll().stream()
                .anyMatch(u -> u.getPersona() != null
                        && u.getPersona().getId().equals(personaId)
                        && !"ELIMINADO".equalsIgnoreCase(u.getEstado()));
        if (personaYaTieneUsuario)
            return ResponseEntity.badRequest().body("La persona seleccionada ya tiene un usuario asignado.");

        // 3) Cargar Persona y Rol
        var persona = personaService.findById(personaId);
        if (persona == null) return ResponseEntity.badRequest().body("Persona no encontrada.");

        var rol = rolService.findById(rolId);
        if (rol == null) return ResponseEntity.badRequest().body("Rol no encontrado.");

        // 4) Guardar
        Usuario nuevo = new Usuario();
        nuevo.setUsername(usernameNorm);                         // usar el normalizado
        nuevo.setPassword(passwordEncoder.encode(rawPassword));  // encriptar
        nuevo.setPersona(persona);
        nuevo.setRol(rol);
        nuevo.setEstado("ACTIVO");

        Usuario logueado = (Usuario) request.getSession().getAttribute("usuario");
        if (logueado != null) nuevo.setRegistroIdUsuario(logueado.getId());

        usuarioService.save(nuevo);
        return ResponseEntity.ok("Usuario registrado correctamente.");
    }



    @ValidarUsuarioAutenticado
    @PostMapping("/modificar-usuario")
    public ResponseEntity<String> modificar(HttpServletRequest request, Usuario usuario) {
        Usuario original = usuarioService.findById(usuario.getId());
        if (original == null) return ResponseEntity.badRequest().body("Usuario no encontrado.");

        // copia campos editables excepto password
        original.setUsername(usuario.getUsername());
        original.setPersona(usuario.getPersona());
        original.setRol(usuario.getRol());
        original.setEstado("ACTIVO");

        Usuario usuarioLogueado = (Usuario) request.getSession().getAttribute("usuario");
        original.setModificacionIdUsuario(usuarioLogueado.getId());

        usuarioService.save(original);
        return ResponseEntity.ok("Se realizó la modificación correctamente");
    }

    // 🔑 Cambiar contraseña (admin define nueva)
    @ValidarUsuarioAutenticado
    @PostMapping("/cambiar-password/{id_usuario}")
    public ResponseEntity<String> cambiarPassword(
            @PathVariable("id_usuario") String idUsuarioEnc,
            @RequestParam("nueva") String nueva,
            @RequestParam("confirmar") String confirmar) throws Exception {

        if (nueva == null || confirmar == null || nueva.isBlank())
            return ResponseEntity.badRequest().body("La contraseña nueva es requerida.");
        if (!nueva.equals(confirmar))
            return ResponseEntity.badRequest().body("Las contraseñas no coinciden.");

        Long id = Long.parseLong(Encriptar.decrypt(idUsuarioEnc));
        Usuario u = usuarioService.findById(id);
        if (u == null) return ResponseEntity.badRequest().body("Usuario no encontrado.");

        u.setPassword(passwordEncoder.encode(nueva));

        usuarioService.save(u);
        return ResponseEntity.ok("Contraseña actualizada.");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_usuario}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_usuario") String idUsuario) throws Exception {

        Long id = Long.parseLong(Encriptar.decrypt(idUsuario));
        Usuario usuario = usuarioService.findById(id);
        usuario.setEstado("ELIMINADO");
        usuarioService.save(usuario);

        return ResponseEntity.ok("Registro Eliminado");
    }
}