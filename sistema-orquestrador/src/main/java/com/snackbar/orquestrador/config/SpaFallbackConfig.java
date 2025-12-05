package com.snackbar.orquestrador.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller para rotas do Angular (SPA).
 * 
 * 1. Mapeia rotas específicas do Angular para retornar index.html
 * 2. Quando uma rota não é encontrada (404) e não é uma rota de API,
 * retorna o index.html para que o Angular Router processe.
 */
@Controller
public class SpaFallbackConfig implements ErrorController {

    /**
     * Rotas públicas do Angular que precisam retornar index.html.
     * Estas são as rotas de auto-atendimento do cliente via QR Code.
     */
    @GetMapping({
            "/pedido-mesa/{token}",
            "/mesa/{token}",
            "/login",
            "/cardapio",
            "/pedidos",
            "/sessoes",
            "/historico-sessoes",
            "/gestao-caixa",
            "/relatorios",
            "/relatorio-financeiro",
            "/gestao-estoque",
            "/administracao",
            "/gestao-mesas",
            "/lobby-pedidos"
    })
    public String forwardToAngular() {
        return "forward:/index.html";
    }

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        String path = (String) request.getAttribute("jakarta.servlet.error.request_uri");

        // Se for rota de API, não fazer fallback (deixar o erro 404)
        if (path != null && (path.startsWith("/api/") ||
                path.startsWith("/actuator/") ||
                path.startsWith("/swagger-ui/") ||
                path.startsWith("/v3/"))) {
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
