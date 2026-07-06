package com.snackbar.pedidos.infrastructure.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.snackbar.pedidos.application.dto.ContaMesaComPixDTO;
import com.snackbar.pedidos.application.dto.ContaMesaDTO;
import com.snackbar.pedidos.application.usecases.ConsultarContaMesaUseCase;
import com.snackbar.pedidos.application.usecases.FecharContaMesaUseCase;

import lombok.RequiredArgsConstructor;

/**
 * Endpoints publicos da conta pos-paga de mesa. Identificacao do cliente via
 * header X-Cliente-Id. Rotas liberadas em SecurityConfig (/api/cliente/conta/**).
 */
@RestController
@RequestMapping("/api/cliente/conta")
@RequiredArgsConstructor
public class ContaMesaRestController {

    private final ConsultarContaMesaUseCase consultarContaMesa;
    private final FecharContaMesaUseCase fecharContaMesa;

    @GetMapping
    public ResponseEntity<ContaMesaDTO> consultar(
            @RequestParam("mesaToken") String mesaToken,
            @RequestHeader("X-Cliente-Id") String clienteId) {
        return ResponseEntity.ok(consultarContaMesa.executar(mesaToken, clienteId));
    }

    @PostMapping("/fechar")
    public ResponseEntity<ContaMesaComPixDTO> fechar(
            @RequestBody FecharContaRequest request,
            @RequestHeader("X-Cliente-Id") String clienteId,
            @RequestHeader("X-Correlation-Id") String correlationId) {
        ContaMesaComPixDTO resposta =
                fecharContaMesa.executar(correlationId, request.mesaToken(), clienteId);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    public record FecharContaRequest(String mesaToken) {
    }
}
