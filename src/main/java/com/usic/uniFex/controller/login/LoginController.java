package com.usic.uniFex.controller.login;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping(value = "/login")
    public String inicioPublico() {
        System.out.println("INGRESNADO CRJ");
        return "login/login";
    }
}
