package com.usic.uniFex.controller.venta;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.uniFex.model.IService.ICategoriaVentaService;
import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IVentaBoletoService;
import com.usic.uniFex.model.entity.CategoriaVenta;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.model.entity.VentaBoleto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;


@Controller
@RequiredArgsConstructor
public class ventaBoleteriaController {

    private final ICategoriaVentaService categoriaVentaService;
    private final IVentaBoletoService ventaBoletoService;
    private final IPersonaService personaService;
    
    @GetMapping("/venta")
    public String ventaBoleto(Model model) {
        List<CategoriaVenta> categoriaVentas = categoriaVentaService.findAll();
        model.addAttribute("categoriasVentas", categoriaVentas);
        return "venta/ventaBoleteria";
    }

    @Transactional
    @PostMapping("/boletos/venta/registrar")
    public String registrarVenta(@RequestParam Long categoriaId,
                                 @RequestParam Integer cantidad,
                                 @RequestParam String nombre,
                                 @RequestParam String paterno,
                                 @RequestParam(required = false) String materno,
                                 @RequestParam String ci,
                                 @RequestParam String celular,
                                 RedirectAttributes ra,
                                 HttpServletRequest request) {

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");

        // 1) Persona: buscar por CI; si no existe, crear
        Persona persona = personaService.findFirstByCi(ci).orElseGet(Persona::new);
        persona.setNombre(nombre != null ? nombre.trim() : null);
        persona.setPaterno(paterno != null ? paterno.trim() : null);
        persona.setMaterno(materno != null ? materno.trim() : null);
        persona.setCi(ci != null ? ci.trim() : null);
        persona.setCelular(celular != null ? celular.trim() : null);
        persona.setRegistroIdUsuario(usuario.getId());

        // Si agregaste el campo:
        try {
            persona.setEstado("CLIENTE");
        } catch (Exception ignored) { /* si no existe el campo, no pasa nada */ }

        persona = personaService.save(persona);

        // 2) Categoría + precio real desde BD
        CategoriaVenta cat = categoriaVentaService.findById(categoriaId);

        // Parseo precio (String -> BigDecimal) o usa cat.getPrecio() si es BigDecimal
        BigDecimal unit;
        try {
            unit = new BigDecimal(cat.getPrecio());
        } catch (Exception e) {
            unit = BigDecimal.ZERO;
        }

        int qty = (cantidad == null || cantidad < 1) ? 1 : Math.min(cantidad, 50);
        BigDecimal total = unit.multiply(BigDecimal.valueOf(qty));

        // 3) Guardar venta
        VentaBoleto v = new VentaBoleto();
        v.setPersona(persona);
        v.setCategoria(cat);
        v.setCantidad(qty);
        v.setPrecioUnitario(unit);
        v.setTotal(total);
        v.setFechaHora(LocalDateTime.now());
        v.setEstado("ACTIVO");
        v.setRegistroIdUsuario(usuario.getId());

        ventaBoletoService.save(v);

        // 4) Feedback y redirección
        ra.addFlashAttribute("ok", true);
        ra.addFlashAttribute("ventaId", v.getId());
        ra.addFlashAttribute("cliente", persona.getNombreCompleto());
        ra.addFlashAttribute("categoria", cat.getNombre());
        ra.addFlashAttribute("cantidad", qty);
        ra.addFlashAttribute("total", total);

        return "redirect:/venta"; // crea una vista simple de éxito
    }
}