package com.snackbar.autenticacao.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String ROLE_ADMINISTRADOR = "ADMINISTRADOR";
    private static final String ROLE_OPERADOR = "OPERADOR";
    private static final String PRODUTOS_PATH_PATTERN = "/api/produtos/**";
    private static final String CATEGORIAS_PATH_PATTERN = "/api/categorias/**";
    private static final String PEDIDOS_PATH_PATTERN = "/api/pedidos/**";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoints públicos (sem autenticação)
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("GET", "/api/status").permitAll()

                        // Endpoints de autenticação (exigem autenticação)
                        .requestMatchers("/api/auth/**").authenticated()

                        // Endpoints administrativos (exigem role ADMINISTRADOR)
                        .requestMatchers("/api/admin/**").hasRole(ROLE_ADMINISTRADOR)

                        // Endpoints de cardápio - Leitura para ADMINISTRADOR e OPERADOR, escrita apenas
                        // ADMINISTRADOR
                        .requestMatchers("GET", PRODUTOS_PATH_PATTERN)
                        .hasAnyRole(ROLE_ADMINISTRADOR, ROLE_OPERADOR)
                        .requestMatchers("POST", PRODUTOS_PATH_PATTERN)
                        .hasRole(ROLE_ADMINISTRADOR)
                        .requestMatchers("PUT", PRODUTOS_PATH_PATTERN)
                        .hasRole(ROLE_ADMINISTRADOR)
                        .requestMatchers("DELETE", PRODUTOS_PATH_PATTERN)
                        .hasRole(ROLE_ADMINISTRADOR)
                        .requestMatchers("GET", CATEGORIAS_PATH_PATTERN)
                        .hasAnyRole(ROLE_ADMINISTRADOR, ROLE_OPERADOR)
                        .requestMatchers("POST", CATEGORIAS_PATH_PATTERN)
                        .hasRole(ROLE_ADMINISTRADOR)
                        .requestMatchers("PUT", CATEGORIAS_PATH_PATTERN)
                        .hasRole(ROLE_ADMINISTRADOR)
                        .requestMatchers("DELETE", CATEGORIAS_PATH_PATTERN)
                        .hasRole(ROLE_ADMINISTRADOR)

                        // Endpoints de pedidos - ADMINISTRADOR e OPERADOR
                        .requestMatchers(PEDIDOS_PATH_PATTERN).hasAnyRole(ROLE_ADMINISTRADOR, ROLE_OPERADOR)

                        // Endpoints de lobby de pedidos - ADMINISTRADOR e OPERADOR
                        .requestMatchers("/api/lobby-pedidos", "/api/lobby-pedidos/**")
                        .hasAnyRole(ROLE_ADMINISTRADOR, ROLE_OPERADOR)

                        // Endpoints de sessões de trabalho - Leitura para ADMINISTRADOR e OPERADOR,
                        // escrita apenas ADMINISTRADOR
                        .requestMatchers("GET", "/api/sessoes-trabalho", "/api/sessoes-trabalho/**")
                        .hasAnyRole(ROLE_ADMINISTRADOR, ROLE_OPERADOR)
                        .requestMatchers("POST", "/api/sessoes-trabalho", "/api/sessoes-trabalho/**")
                        .hasRole(ROLE_ADMINISTRADOR)
                        .requestMatchers("PUT", "/api/sessoes-trabalho", "/api/sessoes-trabalho/**")
                        .hasRole(ROLE_ADMINISTRADOR)
                        .requestMatchers("DELETE", "/api/sessoes-trabalho", "/api/sessoes-trabalho/**")
                        .hasRole(ROLE_ADMINISTRADOR)

                        // Endpoints de histórico de sessões - APENAS ADMINISTRADOR
                        .requestMatchers("/api/historico-sessoes", "/api/historico-sessoes/**")
                        .hasRole(ROLE_ADMINISTRADOR)

                        // Endpoints de relatórios - APENAS ADMINISTRADOR
                        .requestMatchers("/api/relatorios", "/api/relatorios/**").hasRole(ROLE_ADMINISTRADOR)
                        .requestMatchers("/api/relatorio-financeiro", "/api/relatorio-financeiro/**")
                        .hasRole(ROLE_ADMINISTRADOR)

                        // Endpoints de clientes - ADMINISTRADOR e OPERADOR (necessário para criar
                        // pedidos)
                        .requestMatchers("/api/clientes", "/api/clientes/**")
                        .hasAnyRole(ROLE_ADMINISTRADOR, ROLE_OPERADOR)

                        // Endpoints de configuração de animação - Leitura para ADMINISTRADOR e
                        // OPERADOR, escrita apenas ADMINISTRADOR
                        .requestMatchers("GET", "/api/config-animacao", "/api/config-animacao/**")
                        .hasAnyRole(ROLE_ADMINISTRADOR, ROLE_OPERADOR)
                        .requestMatchers("POST", "/api/config-animacao", "/api/config-animacao/**")
                        .hasRole(ROLE_ADMINISTRADOR)
                        .requestMatchers("PUT", "/api/config-animacao", "/api/config-animacao/**")
                        .hasRole(ROLE_ADMINISTRADOR)
                        .requestMatchers("DELETE", "/api/config-animacao", "/api/config-animacao/**")
                        .hasRole(ROLE_ADMINISTRADOR)

                        // Endpoints de impressão - Configuração apenas ADMINISTRADOR, impressão
                        // ADMINISTRADOR e OPERADOR
                        .requestMatchers("GET", "/api/impressao/configuracao")
                        .hasAnyRole(ROLE_ADMINISTRADOR, ROLE_OPERADOR)
                        .requestMatchers("POST", "/api/impressao/configuracao")
                        .hasRole(ROLE_ADMINISTRADOR)
                        .requestMatchers("POST", "/api/impressao/cupom-fiscal")
                        .hasAnyRole(ROLE_ADMINISTRADOR, ROLE_OPERADOR)

                        // Qualquer outro endpoint exige autenticação por padrão
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
