package com.usic.uniFex.controller.inscripcion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.service.NotaVentaCodigoService;

import lombok.RequiredArgsConstructor;

/**
 * Comprobacion publica de una nota de venta.
 *
 * Es publico a proposito: el sentido del codigo es que CUALQUIERA con el papel en la mano
 * —alguien en la puerta, el propio expositor— pueda confirmar que la venta existe, y esa
 * gente no tiene cuenta en el sistema.
 *
 * Por eso mismo devuelve lo minimo para decidir: si existe, si sigue vigente, a nombre de que
 * entidad y cuando se emitio. Nada de responsables, importes ni datos de contacto: seria
 * regalar informacion de las ventas a quien pruebe codigos al azar.
 */
@RestController
@RequestMapping("/api/publico/notas")
@RequiredArgsConstructor
public class VerificacionApiController {

    private final NotaVentaCodigoService codigoService;

    @GetMapping("/{codigo}")
    public NotaVentaCodigoService.Verificacion verificar(@PathVariable String codigo) {
        return codigoService.verificar(codigo);
    }
}
