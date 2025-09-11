package com.usic.uniFex.controller.responsables;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.usic.uniFex.Config.Encriptar;
import com.usic.uniFex.anotacion.ValidarUsuarioAutenticado;
import com.usic.uniFex.model.IService.IInscripcionPuestoService;
import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.IService.IResponsableService;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/administracion/responsables")
public class ResponsablesController {

    private final IResponsableService responsableService;

    private final IInscripcionService inscripcionService;
    
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista_responsables() {
        return "responsables/vista";
    }

    @RequestMapping(value = "/tabla-registros", method = {RequestMethod.GET, RequestMethod.POST})
    public String tabla_responsbales(Model model) {
        model.addAttribute("responsables", responsableService.listarParaTabla());
        return "responsables/tabla_registro";
    }

    /* RESPONSABLES RUEDA */
    @ValidarUsuarioAutenticado
    @GetMapping("/vistaRA")
    public String vista_responsablesR() {
        return "responsable_rueda/vista";
    }

    @RequestMapping(value = "/tabla-registrosR", method = {RequestMethod.GET, RequestMethod.POST})
    public String tabla_responsbalesR(Model model) {
        model.addAttribute("responsables", responsableService.listarVista());
        return "responsable_rueda/tabla_registro";
    }

    // @ValidarUsuarioAutenticado
    // @PostMapping("/formulario")
    // public String formulario(Model model, Usuario usuario) {
    //     model.addAttribute("usuario", new Usuario());
    //     // model.addAttribute("listaPersonas", personaService.listarPersonas());
    //     // model.addAttribute("listaRoles", rolService.findAll());

    //     return "publico/formulario";
    // }

    // @ValidarUsuarioAutenticado
    // @PostMapping("/formulario-edit/{id_usuario}")
    // public String formularioEdit(Model model, @PathVariable("id_usuario") String idUsuario) throws Exception {

    //     Long id = Long.parseLong(Encriptar.decrypt(idUsuario));
    //     // model.addAttribute("usuario", usuarioService.findById(id));
    //     // model.addAttribute("listaPersonas", personaService.listarPersonas());
    //     // model.addAttribute("listaRoles", rolService.findAll());
    //     model.addAttribute("edit", "true");

    //     return "publico/formulario";
    // }




    @ValidarUsuarioAutenticado
    @GetMapping("/formulario-edit/{id_inscripcion}")
    public String modalResponsable(@PathVariable("id_inscripcion") Long id_inscripcion,Model model) {
        
        model.addAttribute("inscripcion", inscripcionService.findById(id_inscripcion));


        model.addAttribute("edit", "true");

        return "publico/formulario :: modalContent";
    }


    @ValidarUsuarioAutenticado
    @PostMapping("/modificar-inscripcion")
    public ResponseEntity<String> modificar(HttpServletRequest request, Inscripcion inscripcion) {
        // Inscripcion original = usuarioService.findById(usuario.getId());
        // if (original == null) return ResponseEntity.badRequest().body("Usuario no encontrado.");

        // // copia campos editables excepto password
        // original.setUsername(usuario.getUsername());
        // original.setPersona(usuario.getPersona());
        // original.setRol(usuario.getRol());
        // original.setEstado("ACTIVO");

        // Usuario usuarioLogueado = (Usuario) request.getSession().getAttribute("usuario");
        // original.setModificacionIdUsuario(usuarioLogueado.getId());

        // usuarioService.save(original);
        return ResponseEntity.ok("Se realizó la modificación correctamente");
    }
}
