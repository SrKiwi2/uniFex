package com.usic.uniFex.controller.admin;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
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
import com.usic.uniFex.model.entity.Entidad;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.InscripcionPuesto;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.entity.Responsable;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.model.repository.FuncionesInscripcion;
import com.usic.uniFex.model.service.FileStorageService;
import com.usic.uniFex.model.service.ReciboPdfService;
import com.usic.uniFex.model.service.FileStorageService.Bucket;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final ReciboPdfService reciboPdfService;
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final FileStorageService storage;

    @ValidarUsuarioAutenticado
    @GetMapping(value = "/admin")
    public String adminIndex(HttpServletRequest request, Model model) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        Persona persona = usuario.getPersona();
        model.addAttribute("tiposEntidads", tipoEntidadService.findAll());
        model.addAttribute("categorias", categoriaService.findAll());
        request.getSession().setAttribute("persona", persona);
        logger.info("Usuario en sesión: {}", usuario.getPersona().getNombre());
        return "inicio-admin";
    }

    @GetMapping("/api/puestos-libres")
    @ResponseBody
    public List<Map<String, Object>> puestosLibresPorCategoria(@RequestParam("categoriaId") Long categoriaId) {
        return puestoService.listarLibresPorCategoria(categoriaId)
            .stream()
            .map(p -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", p.getId());
                map.put("codigo", p.getCodigo());
                map.put("tamano", p.getTamano());
                map.put("categoriaId", p.getCategoria().getId());
                return map;
            })
            .collect(Collectors.toList());
    }

    @ValidarUsuarioAutenticado
    @GetMapping(value = "/vistaR")
    public String Index(@RequestParam(value = "inscripcionId", required = false) Long inscripcionId, HttpServletRequest request, Model model) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        model.addAttribute("tiposEntidads", tipoEntidadService.findAll());
        model.addAttribute("categorias", categoriaService.findAll());
        model.addAttribute("puestos", puestoService.findAll());
        model.addAttribute("inscripcionId", inscripcionId);
        logger.info("Usuario en sesión: {}", usuario.getPersona().getNombre());

        Persona persona = usuario.getPersona();

        request.getSession().setAttribute("persona", persona);
        logger.info("inscripcionId (flash): {}", inscripcionId);

        System.out.println("vista logueada con responsable");
        return "publico/responsable";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/guardar")
    public String adminIndexGuardar(
            HttpServletRequest request, Model model,
            @RequestParam("nombreEntidad") String nombreEntidad,
            @RequestParam("nitEntidad") String nitEntidad,
            @RequestParam("descripcionEntidad") String descripcionEntidad,
            @RequestParam("objetoContratacion") String objetoContratacion,
            @RequestParam("fechaInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam("fechaFin")    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam("tipoEntidad") Long tipoEntidad,
            @RequestParam("nombreResponsable1") String nombreResponsable1,
            @RequestParam("paternoResponsable1") String paternoResponsable1,
            @RequestParam("maternoResponsable1") String maternoResponsable1,
            @RequestParam("ciResponsable1") String ciResponsable1,
            @RequestParam("correoResponsable1") String correoResponsable1,
            @RequestParam("celularResponsable1") String celularResponsable1,
            @RequestParam(value = "fotoResponsable1", required = false) MultipartFile fotoResponsable1,
            @RequestParam(value = "nombreResponsable2", required = false) String nombreResponsable2,
            @RequestParam(value = "paternoResponsable2", required = false) String paternoResponsable2,
            @RequestParam(value = "maternoResponsable2", required = false) String maternoResponsable2,
            @RequestParam(value = "ciResponsable2", required = false) String ciResponsable2,
            @RequestParam(value = "correoResponsable2", required = false) String correoResponsable2,
            @RequestParam(value = "celularResponsable2", required = false) String celularResponsable2,
            @RequestParam(value = "fotoResponsable2", required = false) MultipartFile fotoResponsable2,
            @RequestParam(value = "puestosSeleccionados", required = false) List<Long> puestosSeleccionados,
            @RequestParam(value = "numComprobante", required = false) Long numComprobante,
            @RequestParam(value = "comprobante", required = false) MultipartFile comprobante,
            @RequestParam(value = "pagoContado", defaultValue = "false") boolean pagoContado,

            RedirectAttributes flash
    ) {

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");

        Entidad entidad = new Entidad();
        entidad.setNombre(nombreEntidad);
        entidad.setNit(nitEntidad);
        entidad.setDescripcion(descripcionEntidad);
        entidad.setObjeto(objetoContratacion);
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
        persona1.setEstado("RESPONSABLE");
        persona1.setRegistro(new Date());
        persona1.setRegistroIdUsuario(usuario.getId());
        persona1.setModificacion(new Date());
        persona1.setModificacionIdUsuario(usuario.getId());

        try {
            String foto1Rel = storage.save(fotoResponsable1, Bucket.RESPONSABLES,
                    nombreResponsable1 + " " + paternoResponsable1 + " " + maternoResponsable1);
            persona1.setFoto(foto1Rel);
        } catch (IOException e) {
            logger.warn("No se pudo guardar foto de responsable 1: {}", e.getMessage());
            persona1.setFoto(null);
        }
        personaService.save(persona1);

        Responsable responsable1 = new Responsable();
        responsable1.setEntidad(entidad);
        responsable1.setPersona(persona1);
        responsable1.setEstado("RESPONSABLE");
        responsable1.setRegistro(new Date());
        responsable1.setRegistroIdUsuario(usuario.getId());
        responsable1.setModificacion(new Date());
        responsable1.setModificacionIdUsuario(usuario.getId());
        responsableService.save(responsable1);

        boolean hayResp2 =
            notBlank(nombreResponsable2) || notBlank(paternoResponsable2) || notBlank(maternoResponsable2) ||
            notBlank(ciResponsable2) || notBlank(correoResponsable2) || notBlank(celularResponsable2) ||
            (fotoResponsable2 != null && !fotoResponsable2.isEmpty());

        if (hayResp2) {
            Persona persona2 = new Persona();
            persona2.setNombre(nvl(nombreResponsable2));
            persona2.setPaterno(nvl(paternoResponsable2));
            persona2.setMaterno(nvl(maternoResponsable2));
            persona2.setCi(nvl(ciResponsable2));
            persona2.setCorreo(nvl(correoResponsable2));
            persona2.setCelular(nvl(celularResponsable2));
            persona2.setEstado("RESPONSABLE");
            persona2.setRegistro(new Date());
            persona2.setRegistroIdUsuario(usuario.getId());
            persona2.setModificacion(new Date());
            persona2.setModificacionIdUsuario(usuario.getId());

            try {
                String foto2Rel = storage.save(fotoResponsable2, Bucket.RESPONSABLES,
                        (nombreResponsable2 + " " + paternoResponsable2 + " " + maternoResponsable2).trim());
                persona2.setFoto(foto2Rel);
            } catch (IOException e) {
                logger.warn("No se pudo guardar foto de responsable 2: {}", e.getMessage());
                persona2.setFoto(null);
            }
            personaService.save(persona2);

            Responsable responsable2 = new Responsable();
            responsable2.setEntidad(entidad);
            responsable2.setPersona(persona2);
            responsable2.setEstado("RESPONSABLE");
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
        inscripcion.setFechaInicio(java.sql.Date.valueOf(fechaInicio));
        inscripcion.setFechaFin(java.sql.Date.valueOf(fechaFin));

        if (pagoContado) {
            inscripcion.setPagoContado(true);
            inscripcion.setNumComprobante(null);
            inscripcion.setImgComprobante(null);
        } else {
            inscripcion.setPagoContado(false);
            inscripcion.setNumComprobante(numComprobante);
            try {
                String compRel = storage.save(comprobante, Bucket.COMPROBANTES, entidad.getNombre());
                inscripcion.setImgComprobante(compRel);
            } catch (IOException e) {
                logger.warn("No se pudo guardar comprobante: {}", e.getMessage());
                inscripcion.setImgComprobante(null);
            }
        }
        inscripcionService.save(inscripcion);

        // ===== PUESTOS =====
        if (puestosSeleccionados != null) {
            for (Long puestoId : puestosSeleccionados) {
                Puesto puesto = puestoService.findById(puestoId);
                puesto.setEstadoPuesto("O");
                puesto.setRegistro(new Date());
                puesto.setRegistroIdUsuario(usuario.getId());
                puesto.setModificacion(new Date());
                puesto.setModificacionIdUsuario(usuario.getId());
                puestoService.save(puesto);

                InscripcionPuesto ip = new InscripcionPuesto();
                ip.setPuesto(puesto);
                ip.setInscripcion(inscripcion);
                ip.setRegistro(new Date());
                ip.setRegistroIdUsuario(usuario.getId());
                ip.setModificacion(new Date());
                ip.setModificacionIdUsuario(usuario.getId());
                ip.setEstado("ACTIVO");
                ip.setCosto(funcionesInscripcion.obtenerCostoPuesto(
                        entidad.getTipoEntidad().getId(),
                        puesto.getTamano(),
                        puesto.getCategoria().getId()
                ));

                inscripcionPuestoService.save(ip);
            }
        }
       flash.addAttribute("inscripcionId", inscripcion.getId());
        return "redirect:/vistaR";
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/ver/inscripcion/{id}/recibo.pdf")
    public void verRecibo(
            @PathVariable Long id,
            HttpServletResponse response) throws Exception {

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=recibo-" + id + ".pdf");
        reciboPdfService.generarRecibo(id, response.getOutputStream());
        response.flushBuffer();
    }

    // Helpers
    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
    private String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    @Value("${app.upload-dir:uploads}")
    private String uploadRoot;

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
        String respuesta = "ADMINISTRATIVO".equals(rol) ? "Inicio Responsable" : "Iniciando Session";
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
