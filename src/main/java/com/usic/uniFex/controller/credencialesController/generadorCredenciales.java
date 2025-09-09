package com.usic.uniFex.controller.credencialesController;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.usic.uniFex.anotacion.ValidarUsuarioAutenticado;
import com.usic.uniFex.model.IService.ICategoriaService;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.model.repository.FuncionesInscripcion;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class generadorCredenciales {

    @Autowired
    private FuncionesInscripcion funcionesInscripcion;

    @Autowired
    private ICategoriaService iCategoriaService;



    
    @GetMapping(value = "/vistaGenerarCredenciales/")
    public String vistaGenerarCredenciales(HttpServletRequest request, Model model) {
        
        model.addAttribute("categorias", iCategoriaService.findAll());
        model.addAttribute("InscripcionesCategoria", funcionesInscripcion.fn_inscripciones_por_categoria());

        return "credenciales/vistaCredencialesGenerador";
    }

    @GetMapping(value = "/credenciales/{id_inscripcion}")
    public String credenciales(HttpServletRequest request, Model model, @PathVariable("id_inscripcion")Long id_inscripcion) {
        
        model.addAttribute("inscripciones", funcionesInscripcion.obtener_datos_inscripcion(id_inscripcion));

        return "credenciales/vistaCredencialesGenerador2";
    }
}
