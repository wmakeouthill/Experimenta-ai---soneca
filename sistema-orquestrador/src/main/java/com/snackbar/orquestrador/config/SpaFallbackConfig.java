package com.snackbar.orquestrador.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Fallback controller para rotas do Angular (SPA).
 * 
 * Quando uma rota não é encontrada (404) e não é uma rota de API,
 * retorna o index.html para que o Angular Router processe.
 */
@Controller
public class SpaFallbackConfig implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        String path = (String) request.getAttribute("jakarta.servlet.error.request_uri");
        
        // Se for rota de API, não fazer fallback (deixar o erro 404)
        if (path != null && (
            path.startsWith("/api/") ||
            path.startsWith("/actuator/") ||
            path.startsWith("/swagger-ui/") ||
            path.startsWith("/v3/")
        )) {
            return "forward:/error";
        }
        
        // Se for arquivo estático (contém ponto no nome), não fazer fallback
        if (path != null && path.matches(".*\\.[a-zA-Z0-9]+$")) {
            return "forward:/error";
        }
        
        // Para rotas do Angular, retornar index.html
        return "forward:/index.html";
    }
}

