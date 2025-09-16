package com.usic.uniFex.controller.responsables;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IResponsableService;
import com.usic.uniFex.model.IService.ITipoEntidadService;
import com.usic.uniFex.model.entity.Entidad;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Responsable;
import com.usic.uniFex.model.service.FileStorageService;
import com.usic.uniFex.model.service.FileStorageService.Bucket;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/administracion/responsables")
public class ResponsablesController {

    private final IResponsableService responsableService;

    private final IInscripcionService inscripcionService;
    private final ITipoEntidadService tipoEntidadService;

    private final IEntidadService entidadService;

    private final IPersonaService personaService;

    private final FileStorageService storage;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista_responsables() {
        return "responsables/vista";
    }

    @RequestMapping(value = "/tabla-registros", method = { RequestMethod.GET, RequestMethod.POST })
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

    @RequestMapping(value = "/tabla-registrosR", method = { RequestMethod.GET, RequestMethod.POST })
    public String tabla_responsbalesR(Model model) {
        model.addAttribute("responsables", responsableService.listarVista());
        return "responsable_rueda/tabla_registro";
    }

    // @ValidarUsuarioAutenticado
    // @PostMapping("/formulario")
    // public String formulario(Model model, Usuario usuario) {
    // model.addAttribute("usuario", new Usuario());
    // // model.addAttribute("listaPersonas", personaService.listarPersonas());
    // // model.addAttribute("listaRoles", rolService.findAll());

    // return "publico/formulario";
    // }

    // @ValidarUsuarioAutenticado
    // @PostMapping("/formulario-edit/{id_usuario}")
    // public String formularioEdit(Model model, @PathVariable("id_usuario") String
    // idUsuario) throws Exception {

    // Long id = Long.parseLong(Encriptar.decrypt(idUsuario));
    // // model.addAttribute("usuario", usuarioService.findById(id));
    // // model.addAttribute("listaPersonas", personaService.listarPersonas());
    // // model.addAttribute("listaRoles", rolService.findAll());
    // model.addAttribute("edit", "true");

    // return "publico/formulario";
    // }

    // @ValidarUsuarioAutenticado
    // @GetMapping("/formulario-edit/{id_inscripcion}")
    // public String modalResponsable(@PathVariable("id_inscripcion") Long
    // id_inscripcion, Model model) {

    // model.addAttribute("inscripcion",
    // inscripcionService.findById(id_inscripcion));
    // model.addAttribute("responsables", responsableService.listarVista());
    // return "publico/formulario :: modalContent";
    // }

    // @ValidarUsuarioAutenticado
    // @GetMapping("/formulario-edit/{id_inscripcion}")
    // public String modalResponsable(@PathVariable("id_inscripcion") Long idInscripcion,
    //                             Model model) {

    //     // 1) Buscar inscripción (404 si no existe)
    //     Inscripcion ins = inscripcionService.findById(idInscripcion);
    //     if (ins == null) {
    //         throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscripción no encontrada");
    //     }

    //     // 2) Asegurar entidad no nula
    //     if (ins.getEntidad() == null) {
    //         ins.setEntidad(new Entidad());
    //     }

    //     // 3) Cargar responsables de la entidad (si tiene ID)
    //     List<Responsable> responsables = Optional
    //             .ofNullable(ins.getEntidad().getId())
    //             .map(entidadId -> responsableService.findByEntidadIdWithPersona(entidadId))
    //             .orElseGet(List::of);

    //     // 4) Armar formulario/DTO
    //     ResponsablesEditForm respForm = new ResponsablesEditForm();
    //     respForm.setEntidadId(ins.getEntidad().getId());
    //     respForm.setInscripcionId(ins.getId());

    //     for (Responsable r : responsables) {
    //         ResponsablePersonaDTO dto = new ResponsablePersonaDTO();
    //         dto.setResponsableId(r.getId());

    //         Persona p = r.getPersona();
    //         if (p != null) {
    //             dto.setPersonaId(p.getId());
    //             dto.setNombre(p.getNombre());
    //             dto.setPaterno(p.getPaterno());
    //             dto.setMaterno(p.getMaterno());
    //             dto.setCi(p.getCi());
    //             dto.setCorreo(p.getCorreo());
    //             dto.setCelular(p.getCelular());
    //         }

    //         respForm.getResponsables().add(dto);
    //     }

    //     // 5) Rellenar hasta 2 slots para el UI
    //     while (respForm.getResponsables().size() < 2) {
    //         respForm.getResponsables().add(new ResponsablePersonaDTO());
    //     }

    //     // 6) Atributos para la vista
    //     model.addAttribute("inscripcion", ins);
    //     model.addAttribute("tipoEntidades", tipoEntidadService.findAll());
    //     model.addAttribute("responsablesForm", respForm);
    //     model.addAttribute("edit", "true");

    //     // 7) Fragmento a renderizar
    //     return "publico/formulario :: modalContent";
    // }


    // @PostMapping("/modificar-inscripcion")
    // public ResponseEntity<String> modificar(
    //         @ModelAttribute Inscripcion inscripcion,
    //         @RequestParam(value = "comprobante", required = false) MultipartFile comprobante) {

    //     System.out.println(inscripcion.getId());

    //     Inscripcion existente = inscripcionService.findById(inscripcion.getId());

    //     // actualizar datos de la inscripción
    //     existente.setEntidadBancaria(inscripcion.getEntidadBancaria());
    //     existente.setNumComprobante(inscripcion.getNumComprobante());

    //     // actualizar datos de la entidad asociada
    //     Entidad entidadExistente = existente.getEntidad();
    //     if (entidadExistente == null) {
    //         entidadExistente = new Entidad();
    //         entidadExistente.setResponsables(new ArrayList<>()); // <- importante
    //     }

    //     entidadExistente.setNombre(inscripcion.getEntidad().getNombre());
    //     entidadExistente.setNit(inscripcion.getEntidad().getNit());
    //     entidadExistente.setDescripcion(inscripcion.getEntidad().getDescripcion());
    //     entidadExistente.setObjeto(inscripcion.getEntidad().getObjeto());
    //     existente.setEntidad(entidadExistente);

    //     // manejar comprobante
    //     if (comprobante != null && !comprobante.isEmpty()) {
    //         String fileName = comprobante.getOriginalFilename();
    //         existente.setImgComprobante(fileName);
    //     }

    //     List<Responsable> nuevosResponsables = inscripcion.getEntidad().getResponsables();
    //     if (nuevosResponsables != null) {
    //         List<Responsable> filtrados = nuevosResponsables.stream()
    //                 .filter(r -> r.getPersona() != null && r.getPersona().getNombre() != null
    //                         && !r.getPersona().getNombre().trim().isEmpty())
    //                 .collect(Collectors.toList());

    //         if (entidadExistente.getResponsables() == null) {
    //             entidadExistente.setResponsables(new ArrayList<>());
    //         }

    //         // Map de existentes
    //         Map<Long, Responsable> responsablesExistentesMap = entidadExistente.getResponsables()
    //                 .stream()
    //                 .collect(Collectors.toMap(Responsable::getId, r -> r));

    //         List<Responsable> responsablesActualizados = new ArrayList<>();

    //         for (Responsable r : filtrados) {
    //             if (r.getId() != null) {
    //                 // actualizar responsable existente
    //                 Responsable respExistente = responsablesExistentesMap.get(r.getId());
    //                 if (respExistente != null) {
    //                     Persona p = r.getPersona();
    //                     if (p != null && p.getId() != null) {
    //                         Persona personaExistente = personaService.findById(p.getId());
    //                         if (personaExistente != null) {
    //                             personaExistente.setNombre(p.getNombre());
    //                             respExistente.setPersona(personaExistente);
    //                         }
    //                     }
    //                     responsablesActualizados.add(respExistente);
    //                 }
    //             } else {
    //                 // nuevo responsable válido
    //                 Persona p = r.getPersona();
    //                 if (p != null && (p.getId() == null || p.getId() == 0)) {
    //                     personaService.save(p);
    //                 }
    //                 r.setEntidad(entidadExistente);
    //                 responsablesActualizados.add(r);
    //             }
    //         }

    //         entidadExistente.setResponsables(responsablesActualizados);
    //     }

    //     inscripcionService.save(existente); // gracias a cascada también guarda entidad

    //     return ResponseEntity.ok("Se realizó la modificación correctamente");
    // }


    @ValidarUsuarioAutenticado
    @GetMapping("/formulario-edit/{id_inscripcion}")
    public String modalResponsable(@PathVariable("id_inscripcion") Long id_inscripcion, Model model) {
        model.addAttribute("inscripcion", inscripcionService.findById(id_inscripcion));
        model.addAttribute("tipoEntidades", tipoEntidadService.findAll());
        return "publico/formulario :: modalContent";
    }

    @PostMapping("/modificar-inscripcion")
    public ResponseEntity<String> modificar(
            @ModelAttribute Inscripcion inscripcion,
            @RequestParam(value = "comprobante", required = false) MultipartFile comprobante,
            @RequestParam Map<String, MultipartFile> fotos) {


        System.out.println(inscripcion.getId());
        Inscripcion existente = inscripcionService.findById(inscripcion.getId());
        existente.setEntidadBancaria(inscripcion.getEntidadBancaria());
        existente.setNumComprobante(inscripcion.getNumComprobante());

        Entidad entidadExistente = existente.getEntidad();
        if (entidadExistente == null) {
            entidadExistente = new Entidad();
        }

        entidadExistente.setNombre(inscripcion.getEntidad().getNombre());
        entidadExistente.setNit(inscripcion.getEntidad().getNit());

        entidadExistente.setDescripcion(inscripcion.getEntidad().getDescripcion());
        entidadExistente.setObjeto(inscripcion.getEntidad().getObjeto()); 
        existente.setEntidad(entidadExistente);

        if (comprobante != null && !comprobante.isEmpty()) {

            try {
                String compRel = storage.save(comprobante, Bucket.COMPROBANTES, entidadExistente.getNombre());
                existente.setImgComprobante(compRel);
            } catch (IOException e) {
                existente.setImgComprobante(null);
            }
        }

        List<Responsable> nuevosResponsables = inscripcion.getEntidad().getResponsables();
        if (nuevosResponsables != null) {
            List<Responsable> filtrados = nuevosResponsables.stream()
                .filter(r -> r.getPersona() != null && r.getPersona().getNombre() != null && !r.getPersona().getNombre().trim().isEmpty())
                .collect(Collectors.toList());

            if (entidadExistente.getResponsables() == null) {
                entidadExistente.setResponsables(new ArrayList<>());
            }

            // Map de existentes
            Map<Long, Responsable> responsablesExistentesMap = entidadExistente.getResponsables()
                    .stream()
                    .collect(Collectors.toMap(Responsable::getId, r -> r));

            List<Responsable> responsablesActualizados = new ArrayList<>();

            int index = 0;
            for (Responsable r : filtrados) {
                String key = "fotoResp_" + index;
                System.out.println(key);
                System.out.println(fotos.get(key).getOriginalFilename());
                MultipartFile fotoFile = fotos.get(key);

                if (r.getId() != null) {
                    Responsable respExistente = responsablesExistentesMap.get(r.getId());
                    if (respExistente != null) {
                        Persona personaExistente = personaService.findById(r.getPersona().getId());
                        if (personaExistente != null) {
                            personaExistente.setNombre(r.getPersona().getNombre());
                            personaExistente.setPaterno(r.getPersona().getPaterno());
                            personaExistente.setMaterno(r.getPersona().getMaterno());
                            personaExistente.setCi(r.getPersona().getCi());
                            personaExistente.setCorreo(r.getPersona().getCorreo());
                            personaExistente.setCelular(r.getPersona().getCelular());

                            if (fotoFile != null && !fotoFile.isEmpty()) {

                                try {
                                    String compRel = storage.save(fotoFile, Bucket.RESPONSABLES, personaExistente.getNombre()+"_"+personaExistente.getPaterno()+"_"+personaExistente.getMaterno());
                                    personaExistente.setFoto(compRel);
                                } catch (IOException e) {
                                    personaExistente.setFoto(null);
                                }
                            }

                            respExistente.setPersona(personaExistente);
                        }
                        responsablesActualizados.add(respExistente);
                    }
                } else {
                    Persona p = r.getPersona();
                    if (p != null) {
                        if (fotoFile != null && !fotoFile.isEmpty()) {

                            try {
                                String compRel = storage.save(fotoFile, Bucket.RESPONSABLES, p.getNombre()+"_"+p.getPaterno()+"_"+p.getMaterno());
                                p.setFoto(compRel);
                            } catch (IOException e) {
                                p.setFoto(null);
                            }

                            p.setFoto(fotoFile.getOriginalFilename());
                            
                        }
                        personaService.save(p);
                    }
                    r.setEntidad(entidadExistente);
                    responsablesActualizados.add(r);
                }
                index++;
            }

            entidadExistente.setResponsables(responsablesActualizados);
        }

        inscripcionService.save(existente); // gracias a cascada también guarda entidadinscripcionService.save(existente); // gracias a cascada también guarda entidad
        
        
        return ResponseEntity.ok("Se realizó la modificación correctamente");
    }

}
