package com.snackbar.autenticacao.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.snackbar.autenticacao.domain.entities.Role;
import com.snackbar.autenticacao.domain.entities.Usuario;
import com.snackbar.autenticacao.domain.ports.UsuarioRepositoryPort;
import com.snackbar.autenticacao.domain.services.JwtService;
import com.snackbar.autenticacao.domain.valueobjects.Email;
import com.snackbar.autenticacao.domain.valueobjects.Senha;

import jakarta.servlet.FilterChain;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "token-valido";
    private static final String USUARIO_ID = "usr-1";

    @Mock private JwtService jwtService;
    @Mock private UsuarioRepositoryPort usuarioRepository;
    @Mock private FilterChain filterChain;

    @InjectMocks private JwtAuthenticationFilter filter;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private Usuario usuario(Role role, boolean ativo) {
        Usuario usuario = Usuario.criar("Totem", Email.of("totem@loja.com"), Senha.restaurarHash("hash"), role);
        usuario.restaurarDoBanco(USUARIO_ID, null, null, "Totem", Email.of("totem@loja.com"),
                Senha.restaurarHash("hash"), role, ativo);
        return usuario;
    }

    private Authentication filtrar() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/autoatendimento/pedidos");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void autenticaComPerfilDoBancoQuandoUsuarioAtivo() throws Exception {
        when(jwtService.validarToken(TOKEN)).thenReturn(true);
        when(jwtService.extrairId(TOKEN)).thenReturn(USUARIO_ID);
        when(usuarioRepository.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(usuario(Role.TOTEM, true)));

        Authentication autenticacao = filtrar();

        assertEquals("ROLE_TOTEM", autenticacao.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void naoAutenticaUsuarioDesativadoMesmoComTokenValido() throws Exception {
        when(jwtService.validarToken(TOKEN)).thenReturn(true);
        when(jwtService.extrairId(TOKEN)).thenReturn(USUARIO_ID);
        when(usuarioRepository.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(usuario(Role.TOTEM, false)));

        assertNull(filtrar());
    }

    @Test
    void naoAutenticaUsuarioExcluido() throws Exception {
        when(jwtService.validarToken(TOKEN)).thenReturn(true);
        when(jwtService.extrairId(TOKEN)).thenReturn(USUARIO_ID);
        when(usuarioRepository.buscarPorId(USUARIO_ID)).thenReturn(Optional.empty());

        assertNull(filtrar());
    }
}
