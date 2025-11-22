package com.snackbar.orquestrador.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller para a rota raiz (/).
 * Retorna informações da API quando não há frontend disponível.
 * Quando o frontend Angular estiver buildado, ele será servido automaticamente.
 */
@Controller
public class RootController {
    
    @GetMapping("/")
    public String root() {
        // Verificar se o frontend (index.html) existe
        Resource indexHtml = new ClassPathResource("/static/index.html");
        
        if (indexHtml.exists() && indexHtml.isReadable()) {
            // Frontend existe - fazer forward para index.html (ResourceHandler vai servir)
            return "forward:/index.html";
        }
        
        // Frontend não existe - redirecionar para endpoint de status da API
        return "redirect:/api/status";
    }
}

/**
 * Controller REST para status da API.
 * Usado quando o frontend não está disponível.
 */
@RestController
@RequestMapping("/api")
class ApiStatusController {
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> info = new HashMap<>();
        info.put("application", "Snackbar System - Backend API");
        info.put("version", "1.0.0-SNAPSHOT");
        info.put("status", "running");
        info.put("timestamp", LocalDateTime.now());
        info.put("endpoints", Map.of(
            "produtos", "/api/produtos",
            "categorias", "/api/categorias"
        ));
        info.put("message", "Backend API está funcionando. Se o frontend Angular estiver buildado, ele será servido automaticamente.");
        info.put("frontend", "Se você está vendo esta mensagem, o frontend ainda não foi buildado. Execute 'mvn clean install' para buildar o frontend.");
        
        return ResponseEntity.ok(info);
    }
}

