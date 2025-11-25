package com.snackbar.orquestrador.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração do Spring MVC para garantir suporte ao Angular Router sem
 * interferir nas rotas da API.
 *
 * O Spring Boot já serve automaticamente os recursos em classpath:/static/.
 * Aqui apenas garantimos que qualquer rota não pertencente à API seja
 * encaminhada para o index.html, permitindo que o Angular trate o
 * roteamento no lado do cliente.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String FORWARD_TO_INDEX = "forward:/index.html";
    private static final String PATH_REGEX = "^(?!api$|actuator$|v3$|swagger-ui$)[\\w-]+$";

    @Override
    public void addViewControllers(@NonNull ViewControllerRegistry registry) {
        registry.addViewController("/")
                .setViewName(FORWARD_TO_INDEX);

        registry.addViewController("/{path:" + PATH_REGEX + "}")
                .setViewName(FORWARD_TO_INDEX);

        registry.addViewController("/**/{path:" + PATH_REGEX + "}")
                .setViewName(FORWARD_TO_INDEX);
    }
}
