package com.usic.uniFex.controller.responsables;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.anotacion.ValidarUsuarioAutenticado;
import com.usic.uniFex.model.IService.IEntidadService;
import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.IService.IResponsableService;
import com.usic.uniFex.model.IService.ITipoEntidadService;
import com.usic.uniFex.model.dto.ResponsablePersonaDTO;
import com.usic.uniFex.model.dto.ResponsablesEditForm;
import com.usic.uniFex.model.entity.Entidad;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Responsable;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/administracion/responsables")
public class ResponsablesController {

    private final IResponsableService responsableService;

    private final IInscripcionService inscripcionService;
    private final ITipoEntidadService tipoEntidadService;

    private final IEntidadService entidadService;
    
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
        Inscripcion ins = inscripcionService.findById(id_inscripcion);
        if (ins.getEntidad() == null) ins.setEntidad(new Entidad());

        List<Responsable> lista = ins.getEntidad().getId() == null
            ? List.of()
            : responsableService.findByEntidadIdWithPersona(ins.getEntidad().getId());
        
        ResponsablesEditForm respForm = new ResponsablesEditForm();
        respForm.setEntidadId(ins.getEntidad().getId());
        respForm.setInscripcionId(ins.getId());

        for (Responsable r : lista) {
            Persona p = r.getPersona();
            ResponsablePersonaDTO dto = new ResponsablePersonaDTO();
            dto.setResponsableId(r.getId());
            if (p != null) {
            dto.setPersonaId(p.getId());
            dto.setNombre(p.getNombre());
            dto.setPaterno(p.getPaterno());
            dto.setMaterno(p.getMaterno());
            dto.setCi(p.getCi());
            dto.setCorreo(p.getCorreo());
            dto.setCelular(p.getCelular());
            }
            respForm.getResponsables().add(dto);
        }

        // Rellenar hasta 2 slots para el UI
        while (respForm.getResponsables().size() < 2) {
            respForm.getResponsables().add(new ResponsablePersonaDTO());
        }

        model.addAttribute("inscripcion", ins);
        model.addAttribute("tipoEntidades", tipoEntidadService.findAll());
        model.addAttribute("responsablesForm", respForm);
        model.addAttribute("edit", "true");
        return "publico/formulario :: modalContent";
    }

    @PostMapping("/modificar-inscripcion")
    public ResponseEntity<String> modificar(
            @ModelAttribute Inscripcion inscripcion,
            @RequestParam(value = "comprobante", required = false) MultipartFile comprobante) {

        Inscripcion existente = inscripcionService.findById(inscripcion.getId());

        // actualizar datos de la inscripción
        existente.setEntidadBancaria(inscripcion.getEntidadBancaria());
        existente.setNumComprobante(inscripcion.getNumComprobante());

        // actualizar datos de la entidad asociada
        Entidad entidadExistente = existente.getEntidad();
        if (entidadExistente == null) {
            entidadExistente = new Entidad();
        }
        entidadExistente.setNombre(inscripcion.getEntidad().getNombre());
        entidadExistente.setNit(inscripcion.getEntidad().getNit());
        entidadExistente.setDescripcion(inscripcion.getEntidad().getDescripcion());
        entidadExistente.setObjeto(inscripcion.getEntidad().getObjeto());
        existente.setEntidad(entidadExistente);

        // manejar comprobante
        if (comprobante != null && !comprobante.isEmpty()) {
            String fileName = comprobante.getOriginalFilename();
            existente.setImgComprobante(fileName);
        }

        inscripcionService.save(existente); // gracias a cascada también guarda entidad

        return ResponseEntity.ok("Se realizó la modificación correctamente");
    }



}
