package com.snackbar.clientes.infrastructure.web;

import com.snackbar.clientes.application.dto.*;
import com.snackbar.clientes.application.usecases.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/publico/cliente/auth")
@RequiredArgsConstructor
public class ClienteAuthRestController {

    private final AutenticarClienteUseCase autenticarClienteUseCase;
    private final AutenticarClienteGoogleUseCase autenticarClienteGoogleUseCase;

    /**
     * Login com telefone e senha
     */
    @PostMapping("/login")
    public ResponseEntity<ClienteLoginResponse> login(
            @Valid @RequestBody ClienteLoginRequest request) {
        ClienteLoginResponse response = autenticarClienteUseCase.executar(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Login/Cadastro via Google OAuth
     */
    @PostMapping("/google")
    public ResponseEntity<ClienteLoginResponse> loginGoogle(
            @Valid @RequestBody ClienteGoogleLoginRequest request) {
        ClienteLoginResponse response = autenticarClienteGoogleUseCase.executar(request);
        return ResponseEntity.ok(response);
    }
}

/**
 * Controller para operações autenticadas de conta do cliente
 */
@RestController
@RequestMapping("/api/cliente/conta")
@RequiredArgsConstructor
class ClienteContaRestController {

    private final DefinirSenhaClienteUseCase definirSenhaClienteUseCase;
    private final VincularGoogleUseCase vincularGoogleUseCase;
    private final DesvincularGoogleUseCase desvincularGoogleUseCase;
    private final BuscarClientePorIdUseCase buscarClientePorIdUseCase;

    /**
     * Obtém dados do cliente logado
     * O clienteId virá do token JWT (será implementado via SecurityContext)
     */
    @GetMapping("/me")
    public ResponseEntity<ClienteDTO> me(@RequestHeader("X-Cliente-Id") String clienteId) {
        ClienteDTO cliente = buscarClientePorIdUseCase.executar(clienteId);
        return ResponseEntity.ok(cliente);
    }

    /**
     * Define ou altera senha do cliente
     */
    @PostMapping("/senha")
    public ResponseEntity<ClienteDTO> definirSenha(
            @RequestHeader("X-Cliente-Id") String clienteId,
            @Valid @RequestBody DefinirSenhaClienteRequest request) {
        ClienteDTO cliente = definirSenhaClienteUseCase.executar(clienteId, request);
        return ResponseEntity.ok(cliente);
    }

    /**
     * Vincula conta Google ao cliente
     */
    @PostMapping("/vincular-google")
    public ResponseEntity<ClienteDTO> vincularGoogle(
            @RequestHeader("X-Cliente-Id") String clienteId,
            @Valid @RequestBody VincularGoogleRequest request) {
        ClienteDTO cliente = vincularGoogleUseCase.executar(clienteId, request);
        return ResponseEntity.ok(cliente);
    }

    /**
     * Desvincula conta Google do cliente
     */
    @DeleteMapping("/desvincular-google")
    public ResponseEntity<ClienteDTO> desvincularGoogle(
            @RequestHeader("X-Cliente-Id") String clienteId) {
        ClienteDTO cliente = desvincularGoogleUseCase.executar(clienteId);
        return ResponseEntity.ok(cliente);
    }
}
