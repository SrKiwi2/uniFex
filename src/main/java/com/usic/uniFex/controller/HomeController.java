package com.usic.uniFex.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {
    @GetMapping("/")
  public String home(Model model) {
    model.addAttribute("personaForm", new PersonaForm());
    System.out.println("publico mijo");
    return "publico/index"; // templates/publico/index.html
  }

  @PostMapping("/persona")
  public String registrarPersona(@ModelAttribute PersonaForm personaForm, RedirectAttributes ra) {
    // Por ahora, solo demo: no guardamos en BD. Luego lo conectas a tu servicio.
    ra.addFlashAttribute("ok", "Formulario enviado (demo).");
    return "redirect:/";
  }

  // DTO simple para el form (puedes usar tu entidad si prefieres)
  public static class PersonaForm {
    private String nombre;
    private String paterno;
    private String materno;
    private String ci;
    private String correo;
    private String celular;
    // getters/setters
    public String getNombre(){return nombre;} public void setNombre(String v){nombre=v;}
    public String getPaterno(){return paterno;} public void setPaterno(String v){paterno=v;}
    public String getMaterno(){return materno;} public void setMaterno(String v){materno=v;}
    public String getCi(){return ci;} public void setCi(String v){ci=v;}
    public String getCorreo(){return correo;} public void setCorreo(String v){correo=v;}
    public String getCelular(){return celular;} public void setCelular(String v){celular=v;}
  }
}
