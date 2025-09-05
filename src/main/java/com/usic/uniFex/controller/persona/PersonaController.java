package com.usic.uniFex.controller.persona;

import java.util.*;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.uniFex.Config.Encriptar;
import com.usic.uniFex.anotacion.ValidarUsuarioAutenticado;
import com.usic.uniFex.model.IService.IAdministrativoService;
import com.usic.uniFex.model.IService.ICargoService;
import com.usic.uniFex.model.IService.IOficinaService;
import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IRolService;
import com.usic.uniFex.model.IService.IUsuarioService;
import com.usic.uniFex.model.entity.Administrativo;
import com.usic.uniFex.model.entity.Cargo;
import com.usic.uniFex.model.entity.Oficina;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Rol;
import com.usic.uniFex.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/persona")
@RequiredArgsConstructor
public class PersonaController {
    private final IPersonaService personaService;
    private final IAdministrativoService administrativoService;
    private final IOficinaService oficinaService;
    private final ICargoService cargoService;
    private final IUsuarioService usuarioService;
    private final IRolService rolService;
    private final PasswordEncoder passwordEncoder;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio() {
        return "persona/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model) throws Exception {

        List<Persona> listaPersonas = personaService.listarPersonas();
        List<String> encryptedIds = new ArrayList<>();
        for (Persona personas : listaPersonas) {
            String id_encryptado = Encriptar.encrypt(Long.toString(personas.getId()));
            encryptedIds.add(id_encryptado);
        }
        model.addAttribute("listaPersonas", listaPersonas);
        model.addAttribute("id_encryptado", encryptedIds);

        return "persona/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario(Model model, Persona persona) {
        return "persona/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_persona}")
    public String formularioEdit(Model model, @PathVariable("id_persona") String idPersona) throws Exception {

        Long id = Long.parseLong(Encriptar.decrypt(idPersona));
        model.addAttribute("persona", personaService.findById(id));
        model.addAttribute("edit", "true");

        return "persona/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-persona")
    public ResponseEntity<String> registrar(HttpServletRequest request, @Validated Persona persona) {

        if (personaService.buscarPersonaPorCI(persona.getCi()) == null) {
            // Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
            // persona.setRegistroIdUsuario(usuario.getIdUsuario());
            // persona.setEstado("ACTIVO");
            personaService.save(persona);

            return ResponseEntity.ok("Se realizó el registro correctamente");
        } else {
            return ResponseEntity.ok("Ya existe una persona con este C.I.");
        }
    }

    @PostMapping(value = "/modificar-persona")
    public ResponseEntity<String> modificar(HttpServletRequest request, Persona persona,
            RedirectAttributes redirectAttrs) {

        // Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        // persona.setModificacionIdUsuario(usuario.getIdUsuario());
        // persona.setEstado("ACTIVO");
        // personaService.save(persona);

        return ResponseEntity.ok("Se realizó la modificación correctamente");

    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_persona}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_persona") String idPersona) throws Exception {

        Long id = Long.parseLong(Encriptar.decrypt(idPersona));
        Persona persona = personaService.findById(id);
        persona.setEstado("ELIMINADO");
        personaService.save(persona);

        return ResponseEntity.ok("Registro Eliminado");
    }

    /* registro amdinsitartivo */
    @PostMapping("/registrar-admin")
    @Transactional
    public ResponseEntity<String> registrarAdmin(
            @RequestParam String codFuncionario,
            @RequestParam String ci,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request) {

        try {

            Administrativo administrativo = obtenerAdministrativo(codFuncionario.trim(), ci.trim());

            Rol rolAdmin = rolService.findByNombre("ADMINISTRATIVO").orElseGet(() -> {
                Rol r = new Rol();
                r.setNombre("ADMINISTRATIVO");
                r.setDescripcion("Rol administrativo");

                try { r.getClass().getMethod("setEstado", String.class); r.setEstado("ACTIVO"); } catch (Exception ignore) {}
                return rolService.save(r);
            });

            Usuario user = usuarioService.findByUsername(codFuncionario).orElseGet(Usuario::new);
            user.setUsername(codFuncionario);
            user.setPassword(passwordEncoder.encode(ci));
            user.setPersona(administrativo.getPersona());
            user.setRol(rolAdmin);
            try { user.getClass().getMethod("setEstado", String.class); user.setEstado("ACTIVO"); } catch (Exception ignore) {}
            usuarioService.save(user);

            return ResponseEntity.ok("OK");

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (Exception ex) {
            String msg = "Error procesando: " + ex.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(msg);
        }
    }

    /* API */
    private Administrativo obtenerAdministrativo(String codigoFuncionario, String ci) {
        Administrativo administrativo = administrativoService.findByCodigoFuncionario(codigoFuncionario).orElse(null);
        
        Map<String, String> requestBody = Map.of(
            "usuario", codigoFuncionario,
            "contrasena", ci
        );
    
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("key", "e73b1991c59a67fe182524e4d12da556136ced8a9da310c3af4c4efbde804a10");
    
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
    
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            "http://virtual.uap.edu.bo:7174/api/londraPost/v1/obtenerDatos",
             HttpMethod.POST,
            request,
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
    
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Error consultando API externa.");
        }
    
        Map<String, Object> datos = Objects.requireNonNull(response.getBody());
        String nombre = (String) datos.get("per_nombres");
        String paterno = (String) datos.get("per_ap_paterno");
        String materno = (String) datos.get("per_ap_materno");
        String ciPersona = (String) datos.get("per_num_doc");
        String extension = (String) datos.get("cat_abreviacion");
        String correo = (String) datos.get("perd_email_personal");
        String sexo = (String) datos.get("per_sexo");
        String nombreOficina = (String) datos.get("eo_descripcion");
        String nombreCargo = (String) datos.get("p_descripcion");
    
        Persona persona = personaService.buscarPersonaPorCI(ciPersona);
        if (persona == null) {
            persona = personaService.buscarPersonaPorNombreCompletoUno(nombre, paterno, materno);
        }
    
        if (persona == null) {
            persona = new Persona();
            persona.setNombre(nombre);
            persona.setPaterno(paterno);
            persona.setMaterno(materno);
            persona.setCi(ciPersona);
            persona.setCorreo(correo);
            persona.setEstado("ACTIVO");
            personaService.save(persona);
        }
    
        Oficina oficina = oficinaService.findByNombre(nombreOficina).orElse(null);
        if (oficina == null) {
            oficina = new Oficina();
            oficina.setNombre(nombreOficina.trim());
            oficina.setEstado("ACTIVO");        
            oficina.setRegistro(new Date());
            oficina.setRegistroIdUsuario(1L);
            oficinaService.save(oficina);
        }
    
        Cargo cargo = cargoService.findByNombre(nombreCargo).orElse(null);
        if (cargo == null) {
            cargo = new Cargo();
            cargo.setNombre(nombreCargo);
            cargo.setEstado("ACTIVO");
            cargo.setRegistro(new Date());
            cargo.setRegistroIdUsuario(1L);
            cargoService.save(cargo);
        }
    
        if (administrativo == null) {
            administrativo = new Administrativo();
        }
    
        administrativo.setPersona(persona);
        administrativo.setCargo(cargo);
        administrativo.setOficina(oficina);
        administrativo.setCodigoFuncionario(codigoFuncionario);
        administrativo.setEstado("ACTIVO");
        administrativo.setRegistroIdUsuario(1L);
        administrativo.setRegistro(new Date());
    
        administrativoService.save(administrativo);
    
        return administrativo;
    }
}
