package com.usic.uniFex.controller.administracion;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import com.usic.uniFex.model.IService.IResponsableService;
import com.usic.uniFex.model.entity.Responsable;
import com.usic.uniFex.model.service.ResponsableDetalleRow;

import ch.qos.logback.core.model.Model;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;



@Controller
@RequiredArgsConstructor
public class ControlResponsableController {
    private final IResponsableService responsableService;

    @GetMapping("/control-responsable")
    public String getMethodName() {
        return "controlResponsable/controlResponsable";
    }

    @PostMapping(value = "/buscar-responsable", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> buscarResponsable(@RequestParam("ci") String ci) {

        var dto = responsableService.findDetallePorCi(ci);
        if (dto == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("ok", false, "mensaje", "No se encontró responsable con ese CI"));
        }
        return ResponseEntity.ok(Map.of("ok", true, "data", dto));
    }
    
}