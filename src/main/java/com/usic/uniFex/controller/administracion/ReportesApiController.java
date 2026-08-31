package com.usic.uniFex.controller.administracion;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.dto.ResumenCategoriaView;
import com.usic.uniFex.model.dto.ResumenEntidadView;
import com.usic.uniFex.model.dto.ResumenGeneralView;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

/**
 * Reportes globales de la feria para la SPA (Fase G). Solo administracion: aqui se ve el total
 * (todas las inscripciones, todos los vendedores), a diferencia de "mis ventas" que es lo propio.
 *
 * Se apoya en las proyecciones {@code resumenPor*} de IInscripcionService, que —al reves que
 * fn_get_inscripciones— NO filtran por casetas confirmadas ('O'), asi que cuentan todo lo
 * inscrito y devuelven datos aunque en la copia local los puestos esten reseteados.
 */
@RestController
@RequestMapping("/api/app/reportes")
@RequiredArgsConstructor
@PreAuthorize(Roles.GESTIONA_USUARIOS)
public class ReportesApiController {

    private final IInscripcionService inscripcionService;

    /** KPIs generales: nº de inscripciones, nº de puestos y total en Bs. */
    @GetMapping("/resumen")
    public ResumenGeneralView resumen() {
        return inscripcionService.resumenGeneral();
    }

    /** Desglose por vendedor y categoria (inscripciones, puestos, total). */
    @GetMapping("/por-categoria")
    public List<ResumenCategoriaView> porCategoria() {
        return inscripcionService.resumenPorCategoria();
    }

    /** Desglose por vendedor y entidad (inscripciones, puestos, total). */
    @GetMapping("/por-entidad")
    public List<ResumenEntidadView> porEntidad() {
        return inscripcionService.resumenPorEntidad();
    }
}
