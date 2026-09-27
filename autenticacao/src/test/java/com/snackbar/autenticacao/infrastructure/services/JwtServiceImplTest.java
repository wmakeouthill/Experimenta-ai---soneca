package com.snackbar.autenticacao.infrastructure.services;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.snackbar.autenticacao.domain.entities.Role;
import com.snackbar.autenticacao.domain.entities.Usuario;
import com.snackbar.autenticacao.domain.valueobjects.Email;
import com.snackbar.autenticacao.domain.valueobjects.Senha;
import com.snackbar.autenticacao.infrastructure.config.JwtProperties;

class JwtServiceImplTest {

    private final JwtServiceImpl jwtService = new JwtServiceImpl(propriedades());

    private static JwtProperties propriedades() {
        JwtProperties propriedades = new JwtProperties();
        propriedades.setSecret("segredo-de-teste-com-mais-de-32-caracteres");
        return propriedades;
    }

    private Duration validadeDoToken(Role role) {
        Usuario usuario = Usuario.criar("Fulano", Email.of("fulano@loja.com"), Senha.restaurarHash("hash"), role);
        Instant expiracao = jwtService.extrairClaims(jwtService.gerarToken(usuario)).getExpiration().toInstant();
        return Duration.between(Instant.now(), expiracao);
    }

    @Test
    void tokenDoTotemValeUmAno() {
        assertTrue(validadeDoToken(Role.TOTEM).toDays() >= 364);
    }

    @Test
    void tokenDeOperadorContinuaCurto() {
        assertTrue(validadeDoToken(Role.OPERADOR).toHours() <= 24);
    }
}
