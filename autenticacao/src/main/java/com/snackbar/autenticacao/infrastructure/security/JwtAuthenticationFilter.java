package com.snackbar.autenticacao.infrastructure.security;

import com.snackbar.autenticacao.domain.services.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtService jwtService;
    private static final String BEARER_PREFIX = "Bearer ";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        String token = extrairToken(request);
        
        if (token != null && jwtService.validarToken(token)) {
            String email = jwtService.extrairEmail(token);
            String role = extrairRole(token);
            
            var authorities = Collections.singletonList(
                new SimpleGrantedAuthority(role)
            );
            
            var authentication = new UsernamePasswordAuthenticationToken(
                email, null, authorities
            );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String extrairToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
    
    private String extrairRole(String token) {
        try {
            String role = jwtService.extrairRole(token);
            return role != null ? role : "ROLE_OPERADOR";
        } catch (Exception e) {
            return "ROLE_OPERADOR";
        }
    }
}

