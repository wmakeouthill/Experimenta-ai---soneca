package com.snackbar.autenticacao.infrastructure.security;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.snackbar.autenticacao.domain.ports.UsuarioRepositoryPort;
import com.snackbar.autenticacao.domain.services.JwtService;

/**
 * Regras de autorização por perfil e método. Qualquer rota aceita responde 200 (endpoint
 * coringa), então 403 aqui só pode vir do SecurityConfig.
 */
@SpringJUnitWebConfig(SecurityConfigTest.Config.class)
class SecurityConfigTest {

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class Config {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(mock(JwtService.class), mock(UsuarioRepositoryPort.class));
        }

        @Bean
        EndpointCoringa endpointCoringa() {
            return new EndpointCoringa();
        }
    }

    @RestController
    static class EndpointCoringa {
        @RequestMapping("/api/**")
        ResponseEntity<Void> ok() {
            return ResponseEntity.ok().build();
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp(WebApplicationContext contexto) {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    @ParameterizedTest(name = "{0} {1} como {2} -> {3}")
    @CsvSource({
            // Escrita de recurso administrativo: só ADMINISTRADOR
            "POST,   /api/admin/usuarios,             OPERADOR,      403",
            "POST,   /api/produtos,                   OPERADOR,      403",
            "PUT,    /api/produtos/p1,                OPERADOR,      403",
            "DELETE, /api/produtos/p1,                OPERADOR,      403",
            "PUT,    /api/produtos/p1/adicionais,     OPERADOR,      403",
            "PUT,    /api/categorias/c1,              OPERADOR,      403",
            "POST,   /api/mesas,                      OPERADOR,      403",
            "DELETE, /api/mesas/m1,                   OPERADOR,      403",
            "POST,   /api/sessoes-trabalho,           OPERADOR,      403",
            "PUT,    /api/sessoes-trabalho/s1/pausar, OPERADOR,      403",
            "POST,   /api/config-animacao,            OPERADOR,      403",
            "POST,   /api/impressao/configuracao,     OPERADOR,      403",
            "POST,   /api/produtos,                   TOTEM,         403",
            "PUT,    /api/categorias/c1,              TOTEM,         403",
            "POST,   /api/mesas,                      TOTEM,         403",
            "POST,   /api/produtos,                   ADMINISTRADOR, 200",
            "POST,   /api/admin/usuarios,             ADMINISTRADOR, 200",
            "PUT,    /api/sessoes-trabalho/s1/pausar, ADMINISTRADOR, 200",
            // Leituras e ações do dia a dia continuam liberadas
            "GET,    /api/admin/usuarios,             OPERADOR,      200",
            "GET,    /api/produtos,                   OPERADOR,      200",
            "GET,    /api/sessoes-trabalho/ativa,     OPERADOR,      200",
            "GET,    /api/mesas,                      OPERADOR,      200",
            "GET,    /api/config-animacao,            OPERADOR,      200",
            "POST,   /api/impressao/cupom-fiscal,     OPERADOR,      200",
            "PATCH,  /api/pedidos/p1/status,          OPERADOR,      200",
            "GET,    /api/produtos,                   TOTEM,         200",
            "GET,    /api/mesas/m1,                   TOTEM,         200",
            "GET,    /api/impressao/configuracao,     TOTEM,         200",
            "POST,   /api/impressao/cupom-fiscal/formatar, TOTEM,    200",
            "POST,   /api/impressao/cupom-fiscal,     TOTEM,         403"
    })
    void aplicaRegraPorPerfilEMetodo(String metodo, String path, String perfil, int esperado) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(metodo), path).with(user("u").roles(perfil)))
                .andExpect(status().is(esperado));
    }
}
