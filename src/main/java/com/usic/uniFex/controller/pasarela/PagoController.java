package com.usic.uniFex.controller.pasarela;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.IService.ICategoriaVentaService;
import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IVentaBoletoService;
import com.usic.uniFex.model.dto.PagoRequest;
import com.usic.uniFex.model.entity.CategoriaVenta;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.VentaBoleto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoPasarelaService pasarela;
    private final IPersonaService personaService;
    private final ICategoriaVentaService categoriaVentaService;
    private final IVentaBoletoService ventaBoletoService;

    public PagoController(PagoPasarelaService pasarela,
                          IPersonaService personaService,
                          ICategoriaVentaService categoriaVentaService,
                          IVentaBoletoService ventaBoletoService) {
        this.pasarela = pasarela;
        this.personaService = personaService;
        this.categoriaVentaService = categoriaVentaService;
        this.ventaBoletoService = ventaBoletoService;
    }

    @PostMapping("/crear")
    public ResponseEntity<?> crearPago(@RequestBody PagoRequest request) {
        Map<String, Object> result = pasarela.crearTransaccion(request);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "codigoTransaccion", result.get("codigoTransaccion"),
                "urlRedireccion",  result.get("urlRedireccion")
        ));
    }

    /**
     * Webhook de la pasarela. La pasarela te enviará:
     *  - estado: APROBADO / RECHAZADO / PENDIENTE
     *  - codigoTransaccion
     *  - metadata: lo que enviaste (categoriaId, cantidad, ci, nombre, paterno, materno, celular)
     */

    @PostMapping("/notificacion")
    @Transactional
    public ResponseEntity<?> notificacion(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        String estado = String.valueOf(payload.get("estado"));
        if (!"APROBADO".equalsIgnoreCase(estado)) {
            return ResponseEntity.ok(Map.of("ok", true)); // ignoras los no aprobados
        }

        Map<String, Object> metadata = (Map<String, Object>) payload.get("metadata");
        if (metadata == null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Sin metadata"));
        }

        // 1) Asegurar Persona
        String ci       = String.valueOf(metadata.get("ci"));
        String nombre   = String.valueOf(metadata.get("nombre"));
        String paterno  = String.valueOf(metadata.get("paterno"));
        String materno  = String.valueOf(metadata.get("materno"));
        String celular  = String.valueOf(metadata.get("celular"));
        Persona persona = personaService.findFirstByCi(ci).orElseGet(Persona::new);
        persona.setNombre(nombre);
        persona.setPaterno(paterno);
        persona.setMaterno((materno == null || "null".equalsIgnoreCase(materno)) ? null : materno);
        persona.setCi(ci);
        persona.setCelular(celular);
        try { persona.setEstado("CLIENTE"); } catch (Exception ignored) {}
        persona = personaService.save(persona);

        // 2) Categoría + precio real
        Long categoriaId = Long.valueOf(String.valueOf(metadata.get("categoriaId")));
        CategoriaVenta cat = categoriaVentaService.findById(categoriaId);

        java.math.BigDecimal unit;
        try { unit = new java.math.BigDecimal(cat.getPrecio()); } catch (Exception e) { unit = java.math.BigDecimal.ZERO; }

        int qty = Integer.parseInt(String.valueOf(metadata.get("cantidad")));
        qty = Math.max(1, Math.min(qty, 50));
        java.math.BigDecimal total = unit.multiply(java.math.BigDecimal.valueOf(qty));

        // 3) Guardar venta
        VentaBoleto v = new VentaBoleto();
        v.setPersona(persona);
        v.setCategoria(cat);
        v.setCantidad(qty);
        v.setPrecioUnitario(unit);
        v.setTotal(total);
        v.setFechaHora(java.time.LocalDateTime.now());
        v.setEstado("ACTIVO");
        // Si necesitas el usuario, puedes setear un "usuario sistema" para notificaciones, o guardar el código de transacción:
        // v.setCodigoTransaccion(String.valueOf(payload.get("codigoTransaccion")));
        ventaBoletoService.save(v);

        return ResponseEntity.ok(Map.of("ok", true, "ventaId", v.getId()));
    }
}
