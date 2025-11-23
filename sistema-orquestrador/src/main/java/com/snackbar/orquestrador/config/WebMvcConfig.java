package com.snackbar.orquestrador.config;

import org.springframework.lang.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Configuração do Spring MVC para servir recursos estáticos do frontend.
 * 
 * Quando o frontend Angular for criado e buildado, os arquivos serão
 * copiados para src/main/resources/static/ e servidos automaticamente.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Servir arquivos estáticos do frontend (quando existir)
        // Prioridade: /api/** tem precedência sobre recursos estáticos
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(@NonNull String resourcePath, @NonNull Resource location) throws IOException {
                        // Ignorar rotas de API - deixar os controllers REST tratarem
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        
                        // Tentar encontrar o recurso solicitado
                        Resource requestedResource = location.createRelative(resourcePath);
                        
                        // Se o recurso não existir e não for uma rota de API,
                        // retornar index.html (para suportar Angular Router)
                        if (!requestedResource.exists() || !requestedResource.isReadable()) {
                            Resource indexHtml = new ClassPathResource("/static/index.html");
                            return indexHtml.exists() && indexHtml.isReadable() ? indexHtml : null;
                        }
                        
                        return requestedResource;
                    }
                });
    }
    
    @Override
    public void addViewControllers(@NonNull ViewControllerRegistry registry) {
        // Se houver index.html, redirecionar raiz para ele (frontend Angular)
        // Caso contrário, o RootController retornará informações da API
        // Não configurar aqui para que o RootController tenha prioridade
    }
}

