package com.snackbar.autenticacao.infrastructure.security;

import com.snackbar.autenticacao.domain.entities.Usuario;
import com.snackbar.autenticacao.domain.ports.UsuarioRepositoryPort;
import com.snackbar.autenticacao.domain.services.JwtService;
import com.snackbar.kernel.security.JwtUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepositoryPort usuarioRepository;
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extrairToken(request);

        if (token != null && jwtService.validarToken(token)) {
            autenticar(jwtService.extrairId(token));
        } else if (token != null && request.getRequestURI().contains("/autoatendimento")) {
            log.warn("[JWT-FILTER] Token inválido para: {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Perfil e status vêm do banco, não do token: desativar ou excluir o usuário corta o
     * acesso na hora — é o que torna seguro o token longo do perfil TOTEM.
     * Token sem claim "id" (ex.: token de cliente) não autentica operador.
     */
    // ponytail: 1 SELECT por PK a cada request autenticado; cache curto (ex.: 30s) se o volume crescer.
    private void autenticar(String usuarioId) {
        if (usuarioId == null) {
            return;
        }

        usuarioRepository.buscarPorId(usuarioId)
                .filter(Usuario::estaAtivo)
                .ifPresentOrElse(usuario -> {
                    var authorities = Collections.singletonList(
                            new SimpleGrantedAuthority(usuario.getRole().getAuthority()));
                    var userDetails = new JwtUserDetails(usuario.getEmail().getValor(), usuario.getId());
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(userDetails, null, authorities));
                }, () -> log.warn("[JWT-FILTER] Token de usuário inativo ou inexistente: {}", usuarioId));
    }

    private String extrairToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
