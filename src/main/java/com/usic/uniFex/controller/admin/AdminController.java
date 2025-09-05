package com.usic.uniFex.controller.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.uniFex.anotacion.ValidarUsuarioAutenticado;
import com.usic.uniFex.model.IService.ICategoriaService;
import com.usic.uniFex.model.IService.IEntidadService;
import com.usic.uniFex.model.IService.IInscripcionPuestoService;
import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IPuestoService;
import com.usic.uniFex.model.IService.IResponsableService;
import com.usic.uniFex.model.IService.ITipoEntidadService;
import com.usic.uniFex.model.IService.IUsuarioService;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.entity.Responsable;
import com.usic.uniFex.model.entity.Entidad;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.InscripcionPuesto;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.model.repository.FuncionesInscripcion;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AdminController {

    private final FuncionesInscripcion funcionesInscripcion;

    private final ITipoEntidadService tipoEntidadService;
    private final IInscripcionService inscripcionService;
    private final IInscripcionPuestoService inscripcionPuestoService;
    private final IResponsableService responsableService;
    private final IPersonaService personaService;
    private final IEntidadService entidadService;
    private final ICategoriaService categoriaService;
    private final IPuestoService puestoService;
    private final IUsuarioService IusuarioService;
    private final PasswordEncoder passwordEncoder;
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @ValidarUsuarioAutenticado
    @GetMapping(value = "/admin")
    public String adminIndex(HttpServletRequest request, Model model) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        model.addAttribute("tiposEntidads", tipoEntidadService.findAll());
        model.addAttribute("categorias", categoriaService.findAll());
        model.addAttribute("puestos", puestoService.findAll());

        logger.info("Usuario en sesión: {}", usuario.getPersona().getNombre());

        Persona persona = usuario.getPersona();

        request.getSession().setAttribute("persona", persona);
        return "inicio-admin";
    }

    @ValidarUsuarioAutenticado
    @PostMapping(value = "/admin/guardar")
    public String adminIndexGuardar(HttpServletRequest request, Model model,
        @RequestParam("nombreEntidad") String nombreEntidad,
        @RequestParam("nitEntidad") String nitEntidad,
        @RequestParam("descripcionEntidad") String descripcionEntidad,
        @RequestParam("tipoEntidad") Long tipoEntidad,
        @RequestParam("nombreResponsable1") String nombreResponsable1,
        @RequestParam("paternoResponsable1") String paternoResponsable1,
        @RequestParam("maternoResponsable1") String maternoResponsable1,
        @RequestParam("ciResponsable1") String ciResponsable1,
        @RequestParam("correoResponsable1") String correoResponsable1,
        @RequestParam("celularResponsable1") String celularResponsable1,
        @RequestParam("nombreResponsable2") String nombreResponsable2,
        @RequestParam("paternoResponsable2") String paternoResponsable2,
        @RequestParam("maternoResponsable2") String maternoResponsable2,
        @RequestParam("ciResponsable2") String ciResponsable2,
        @RequestParam("correoResponsable2") String correoResponsable2,
        @RequestParam("celularResponsable2") String celularResponsable2,
        @RequestParam(value = "puestosSeleccionados", required = false) List<Long> puestosSeleccionados
    ) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");

        Entidad entidad = new Entidad();
        entidad.setNombre(nombreEntidad);
        entidad.setNit(nitEntidad);
        entidad.setDescripcion(descripcionEntidad);
        entidad.setTipoEntidad(tipoEntidadService.findById(tipoEntidad));
        entidad.setEstado("ACTIVO");
        entidad.setRegistro(new Date());
        entidad.setRegistroIdUsuario(usuario.getId());
        entidad.setModificacion(new Date());
        entidad.setModificacionIdUsuario(usuario.getId());
        entidadService.save(entidad);

        Persona persona1 = new Persona();
        persona1.setNombre(nombreResponsable1);
        persona1.setPaterno(paternoResponsable1);
        persona1.setMaterno(maternoResponsable1);
        persona1.setCi(ciResponsable1);
        persona1.setCorreo(correoResponsable1);
        persona1.setCelular(celularResponsable1);
        persona1.setEstado("ACTIVO");
        persona1.setRegistro(new Date());
        persona1.setRegistroIdUsuario(usuario.getId());
        persona1.setModificacion(new Date());
        persona1.setModificacionIdUsuario(usuario.getId());
        personaService.save(persona1);

        Responsable responsable1 = new Responsable();
        responsable1.setEntidad(entidad);
        responsable1.setPersona(persona1);
        responsable1.setEstado("ACTIVO");
        responsable1.setRegistro(new Date());
        responsable1.setRegistroIdUsuario(usuario.getId());
        responsable1.setModificacion(new Date());
        responsable1.setModificacionIdUsuario(usuario.getId());
        responsableService.save(responsable1);

        if (!nombreResponsable2.equals("") && !paternoResponsable2.equals("") && !ciResponsable2.equals("")
         && !correoResponsable2.equals("") && !celularResponsable2.equals(""))
            {
            Persona persona2 = new Persona();
            persona2.setNombre(nombreResponsable2);
            persona2.setPaterno(paternoResponsable2);
            persona2.setMaterno(maternoResponsable2);
            persona2.setCi(ciResponsable2);
            persona2.setCorreo(correoResponsable2);
            persona2.setCelular(celularResponsable2);
            persona2.setEstado("ACTIVO");
            persona2.setRegistro(new Date());
            persona2.setRegistroIdUsuario(usuario.getId());
            persona2.setModificacion(new Date());
            persona2.setModificacionIdUsuario(usuario.getId());
            personaService.save(persona2);

            Responsable responsable2 = new Responsable();
            responsable2.setEntidad(entidad);
            responsable2.setPersona(persona2);
            responsable2.setEstado("ACTIVO");
            responsable2.setRegistro(new Date());
            responsable2.setRegistroIdUsuario(usuario.getId());
            responsable2.setModificacion(new Date());
            responsable2.setModificacionIdUsuario(usuario.getId());
            responsableService.save(responsable2);
        }

        Inscripcion inscripcion = new Inscripcion();
        inscripcion.setEntidad(entidad);
        inscripcion.setFechaCompra(LocalDateTime.now());
        inscripcion.setInscripcionEstado("PENDIENTE");
        inscripcion.setEstado("ACTIVO");
        inscripcion.setRegistro(new Date());
        inscripcion.setRegistroIdUsuario(usuario.getId());
        inscripcion.setModificacion(new Date());
        inscripcion.setModificacionIdUsuario(usuario.getId());
        inscripcionService.save(inscripcion);

        if (puestosSeleccionados != null) {
            for (Long puestoId : puestosSeleccionados) {

                Puesto puesto = puestoService.findById(puestoId);
                puesto.setEstadoPuesto("O");
                puesto.setRegistro(new Date());
                puesto.setRegistroIdUsuario(usuario.getId());
                puesto.setModificacion(new Date());
                puesto.setModificacionIdUsuario(usuario.getId());
                puestoService.save(puesto);

                InscripcionPuesto inscripcionPuesto = new InscripcionPuesto();
                inscripcionPuesto.setPuesto(puesto);
                inscripcionPuesto.setInscripcion(inscripcion);
                inscripcionPuesto.setRegistro(new Date());
                inscripcionPuesto.setRegistroIdUsuario(usuario.getId());
                inscripcionPuesto.setModificacion(new Date());
                inscripcionPuesto.setModificacionIdUsuario(usuario.getId());
                inscripcionPuesto.setCosto(BigDecimal.valueOf(
                    funcionesInscripcion.obtenerCostoPuesto(
                        entidad.getTipoEntidad().getId(),
                        puesto.getTamano()
                    )
                ));
                inscripcionPuestoService.save(inscripcionPuesto);
            }
        }
        
        return "redirect:/ver/inscripcion/"+inscripcion.getId();
    }

    @ValidarUsuarioAutenticado
    @GetMapping(value = "/ver/inscripcion/{id_inscripcion}")
    public String verIncripcion(HttpServletRequest request, Model model, @PathVariable("id_inscripcion")Long id_inscripcion) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");

        List<Map<String,Object>> puestos = funcionesInscripcion.obtener_puestos_por_inscripcion(id_inscripcion);

        model.addAttribute("inscripcion", inscripcionService.findById(id_inscripcion));

        double sumaCostosPuestos = puestos.stream()
            .mapToDouble(m -> m.get("costo") != null ? ((Number) m.get("costo")).doubleValue() : 0)
            .sum();

        model.addAttribute("sumaCostosPuestos", sumaCostosPuestos);

        
        return "publico/verInscripcion";
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
