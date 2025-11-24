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
    
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos (sem autenticação)
                .requestMatchers("/api/auth/login").permitAll()
                
                // Endpoints de autenticação (exigem autenticação)
                .requestMatchers("/api/auth/**").authenticated()
                
                // Endpoints administrativos (exigem role ADMINISTRADOR)
                .requestMatchers("/api/admin/**").hasRole("ADMINISTRADOR")
                
                // Endpoints de pedidos - TODAS AS AÇÕES EXIGEM AUTENTICAÇÃO (CRÍTICO)
                .requestMatchers("/api/pedidos", "/api/pedidos/**").authenticated()
                
                // Endpoints de cardápio - leitura pública (GET), escrita exige autenticação
                .requestMatchers("GET", "/api/produtos", "/api/produtos/**").permitAll()
                .requestMatchers("GET", "/api/categorias", "/api/categorias/**").permitAll()
                .requestMatchers("POST", "/api/produtos", "/api/produtos/**").authenticated()
                .requestMatchers("PUT", "/api/produtos/**").authenticated()
                .requestMatchers("DELETE", "/api/produtos/**").hasRole("ADMINISTRADOR")
                .requestMatchers("POST", "/api/categorias", "/api/categorias/**").authenticated()
                
                // Endpoints de sessões de trabalho - exige autenticação
                .requestMatchers("/api/sessoes-trabalho", "/api/sessoes-trabalho/**").authenticated()
                
                // Endpoints de clientes - exige autenticação
                .requestMatchers("/api/clientes", "/api/clientes/**").authenticated()
                
                // Endpoints de configuração de animação - exige autenticação
                .requestMatchers("GET", "/api/config-animacao", "/api/config-animacao/**").authenticated()
                .requestMatchers("POST", "/api/config-animacao", "/api/config-animacao/**").hasRole("ADMINISTRADOR")
                
                // Endpoint de status da API - público (apenas informações básicas)
                .requestMatchers("GET", "/api/status").permitAll()
                
                // Qualquer outro endpoint exige autenticação por padrão
                .anyRequest().authenticated()
            )
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

