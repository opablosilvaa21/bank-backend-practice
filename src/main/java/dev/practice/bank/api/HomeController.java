package dev.practice.bank.api;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Abrir http://localhost:8080 no navegador mostra o painel de clientes (resources/static/index.html). */
@Hidden
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "forward:/index.html";
    }
}
