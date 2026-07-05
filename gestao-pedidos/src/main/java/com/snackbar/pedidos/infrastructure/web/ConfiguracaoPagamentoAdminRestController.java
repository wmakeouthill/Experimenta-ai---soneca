package com.snackbar.pedidos.infrastructure.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.snackbar.pedidos.application.dto.ConfiguracaoPagamentoDTO;
import com.snackbar.pedidos.application.usecases.AtualizarConfiguracaoPagamentoUseCase;
import com.snackbar.pedidos.application.usecases.BuscarConfiguracaoPagamentoUseCase;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Configuracao das flags de pagamento. Protegido por /api/admin/**
 * (role ADMINISTRADOR) no SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/pagamentos/config")
@RequiredArgsConstructor
public class ConfiguracaoPagamentoAdminRestController {

    private final BuscarConfiguracaoPagamentoUseCase buscarUseCase;
    private final AtualizarConfiguracaoPagamentoUseCase atualizarUseCase;

    @GetMapping
    public ResponseEntity<ConfiguracaoPagamentoDTO> buscar() {
        return ResponseEntity.ok(buscarUseCase.executar());
    }

    @PutMapping
    public ResponseEntity<ConfiguracaoPagamentoDTO> atualizar(
            @Valid @RequestBody ConfiguracaoPagamentoDTO request) {
        return ResponseEntity.ok(atualizarUseCase.executar(request));
    }
}
