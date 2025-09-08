package com.usic.uniFex.model.endpoint;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.usic.uniFex.model.service.ReciboPdfService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ReciboPdfController {
    private final ReciboPdfService reciboPdfService;

    @GetMapping("/ver/inscripcion/{id}/recibo.pdf")
    public void verRecibo(@PathVariable Long id, HttpServletResponse resp) throws Exception {
        resp.setContentType("application/pdf");
        resp.setHeader("Content-Disposition", "attachment; filename=recibo-" + id + ".pdf");
        reciboPdfService.generarRecibo(id, resp.getOutputStream());
        resp.flushBuffer();
    }
}
