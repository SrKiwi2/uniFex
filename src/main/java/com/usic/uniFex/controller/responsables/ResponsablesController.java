package com.usic.uniFex.controller.responsables;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
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
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.model.service.FileStorageService;
import com.usic.uniFex.model.service.FileStorageService.Bucket;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
    public String vista_responsables(Model model) {
        model.addAttribute("entidades", entidadService.findAll());
        return "responsables/vista";
    }

    @RequestMapping(value = "/tabla-registros", method = { RequestMethod.GET, RequestMethod.POST })
    public String tabla_responsbales(Model model) {
        model.addAttribute("responsables", responsableService.listarParaTabla());
        return "responsables/tabla_registro";
    }

    // POST: registrar persona + responsable
    @PostMapping(value = "/registrar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> registrar(
            @RequestParam("idEntidad") Long idEntidad,
            @RequestParam("nombre") String nombre,
            @RequestParam("paterno") String paterno,
            @RequestParam(value = "materno", required = false) String materno,
            @RequestParam("ci") String ci,
            @RequestParam(value = "correo", required = false) String correo,
            @RequestParam("celular") String celular,
            @RequestParam(value = "foto", required = false) MultipartFile foto,
            HttpServletRequest request,
            Model model
    ) {
        // 1) Resuelve entidad
        Entidad entidad = entidadService.findById(idEntidad);
        if (entidad == null) {
            model.addAttribute("error", "La entidad seleccionada no existe.");
            model.addAttribute("entidades", entidadService.findAll());
            return ResponseEntity.ok("No está habilitada esta entidad");
        }

        // 2) Usuario de sesión (si usas auditoría por usuario)
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        Long idUsuario = (usuario != null ? usuario.getId() : null);
        Date ahora = new Date();

        // 3) Construye Persona
        Persona persona = new Persona();
        persona.setNombre(nombre != null ? nombre.trim() : null);
        persona.setPaterno(paterno != null ? paterno.trim() : null);
        persona.setMaterno(materno != null ? materno.trim() : null);
        persona.setCi(ci != null ? ci.trim() : null);
        persona.setCorreo(correo != null ? correo.trim() : null);
        persona.setCelular(celular != null ? celular.trim() : null);

        // Auditoría básica (ajusta si tu AuditoriaConfig ya lo maneja)
        persona.setEstado("RESPONSABLE");
        persona.setRegistro(ahora);
        persona.setRegistroIdUsuario(idUsuario);
        persona.setModificacion(ahora);
        persona.setModificacionIdUsuario(idUsuario);

        // 4) Guarda foto (si viene)
        if (foto != null && !foto.isEmpty()) {
            try {
                String nombreBase = (persona.getNombreCompleto().isBlank() ? "responsable" : persona.getNombreCompleto())
                        + "_" + (persona.getCi() == null ? "" : persona.getCi());
                String fotoRel = storage.save(foto, Bucket.RESPONSABLES, nombreBase);
                persona.setFoto(fotoRel); // guarda ruta relativa devuelta por storage
            } catch (IOException e) {
                log.warn("No se pudo guardar foto del responsable: {}", e.getMessage());
                persona.setFoto(null);
            }
        }

        // 5) Persiste Persona
        personaService.save(persona);

        // 6) Crea Responsable vinculado a la Entidad
        Responsable responsable = new Responsable();
        responsable.setPersona(persona);
        responsable.setEntidad(entidad);

        // Auditoría básica
        responsable.setEstado("RESPONSABLE");
        responsable.setRegistro(ahora);
        responsable.setRegistroIdUsuario(idUsuario);
        responsable.setModificacion(ahora);
        responsable.setModificacionIdUsuario(idUsuario);

        // 7) Persiste Responsable
        responsableService.save(responsable);

        // 8) Redirige (ajusta a tu UX: listado o detalle)
        return ResponseEntity.ok("Se realizó el registro correctamente");
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

    Inscripcion existente = inscripcionService.findById(inscripcion.getId());

    // ---- Datos simples de la inscripción ----
    existente.setEntidadBancaria(inscripcion.getEntidadBancaria());
    existente.setNumComprobante(inscripcion.getNumComprobante());

    // ---- Entidad ----
    Entidad entidad = existente.getEntidad();
    if (entidad == null) entidad = new Entidad();

    Entidad entidadReq = inscripcion.getEntidad();
    if (entidadReq != null) {
        entidad.setNombre(entidadReq.getNombre());
        entidad.setNit(entidadReq.getNit());
        entidad.setDescripcion(entidadReq.getDescripcion());
        entidad.setObjeto(entidadReq.getObjeto());
    }
    existente.setEntidad(entidad);

    // ---- Comprobante ----
    if (comprobante != null && !comprobante.isEmpty()) {
        try {
            String compRel = storage.save(comprobante, Bucket.COMPROBANTES, entidad.getNombre());
            existente.setImgComprobante(compRel);
        } catch (IOException e) {
            existente.setImgComprobante(null);
        }
    }

    // ---- Responsables ----
    List<Responsable> entrantes = (entidadReq != null && entidadReq.getResponsables() != null)
            ? entidadReq.getResponsables()
            : List.of();

    if (entidad.getResponsables() == null) entidad.setResponsables(new ArrayList<>());

    // Mapa de existentes (por ID de responsable)
    Map<Long, Responsable> mapExistentes = entidad.getResponsables().stream()
            .filter(r -> r.getId() != null)
            .collect(Collectors.toMap(Responsable::getId, r -> r));

    List<Responsable> resultado = new ArrayList<>();

    for (int i = 0; i < entrantes.size(); i++) {
        Responsable rReq = entrantes.get(i);
        if (rReq.getPersona() == null || rReq.getPersona().getNombre() == null ||
            rReq.getPersona().getNombre().trim().isEmpty()) {
            continue; // *** exige al menos nombre
        }

        MultipartFile foto = fotos.get("fotoResp_" + i); // *** índice intacto

        if (rReq.getId() != null && mapExistentes.containsKey(rReq.getId())) {
            // ---- Actualizar existente ----
            Responsable rDb = mapExistentes.get(rReq.getId());

            // Usa la persona ya asociada en BD
            Persona pDb = rDb.getPersona();
            if (pDb == null) pDb = new Persona();
            Persona pReq = rReq.getPersona();

            pDb.setNombre(pReq.getNombre());
            pDb.setPaterno(pReq.getPaterno());
            pDb.setMaterno(pReq.getMaterno());
            pDb.setCi(pReq.getCi());
            pDb.setCorreo(pReq.getCorreo());
            pDb.setCelular(pReq.getCelular());

            if (foto != null && !foto.isEmpty()) {
                try {
                    String path = storage.save(foto, Bucket.RESPONSABLES,
                            (pDb.getNombre() + "_" + pDb.getPaterno() + "_" + pDb.getMaterno()).trim());
                    pDb.setFoto(path);
                } catch (IOException e) {
                    // deja la foto actual si falla
                }
            }

            personaService.save(pDb);
            rDb.setPersona(pDb);
            rDb.setEntidad(entidad);
            // copia otros campos de Responsable si existen (cargo, area, etc.)
            // rDb.setCargo(rReq.getCargo()); ...

            resultado.add(rDb);

        } else {
            // ---- Crear nuevo ----
            Persona pReq = rReq.getPersona();
            Persona p = new Persona();
            if (pReq != null) {
                p.setNombre(pReq.getNombre());
                p.setPaterno(pReq.getPaterno());
                p.setMaterno(pReq.getMaterno());
                p.setCi(pReq.getCi());
                p.setCorreo(pReq.getCorreo());
                p.setCelular(pReq.getCelular());
            }

            if (foto != null && !foto.isEmpty()) {
                try {
                    String path = storage.save(foto, Bucket.RESPONSABLES,
                            (p.getNombre() + "_" + p.getPaterno() + "_" + p.getMaterno()).trim());
                    p.setFoto(path);
                } catch (IOException e) {
                    // sin foto
                }
            }

            p = personaService.save(p);

            Responsable nuevo = new Responsable();
            nuevo.setPersona(p);
            nuevo.setEntidad(entidad);
            // copia otros campos de Responsable si los tienes en el form
            // nuevo.setCargo(rReq.getCargo()); ...

            resultado.add(nuevo);
        }
    }

    // Reemplaza la lista por la normalizada
    entidad.setResponsables(resultado);

    // Persiste todo (cascadas)
    inscripcionService.save(existente);

    return ResponseEntity.ok("Se realizó la modificación correctamente");
}

}