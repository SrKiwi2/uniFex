package com.usic.uniFex.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping("/mapa")
    public String home() {
        return "forward:/index.html";
    }
}
