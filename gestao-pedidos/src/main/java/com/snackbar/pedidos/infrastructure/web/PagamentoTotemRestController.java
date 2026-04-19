package com.snackbar.pedidos.infrastructure.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.snackbar.pedidos.application.dto.CancelarPagamentoTotemRequest;
import com.snackbar.pedidos.application.dto.ConfirmarPagamentoTotemCartaoRequest;
import com.snackbar.pedidos.application.dto.IniciarPagamentoTotemCartaoRequest;
import com.snackbar.pedidos.application.dto.IniciarPagamentoTotemPixRequest;
import com.snackbar.pedidos.application.dto.PagamentoTotemDTO;
import com.snackbar.pedidos.application.dto.PixCobrancaCriadaDTO;
import com.snackbar.pedidos.application.usecases.BuscarPagamentoTotemUseCase;
import com.snackbar.pedidos.application.usecases.CancelarPagamentoTotemUseCase;
import com.snackbar.pedidos.application.usecases.ConfirmarPagamentoTotemCartaoUseCase;
import com.snackbar.pedidos.application.usecases.IniciarPagamentoTotemCartaoUseCase;
import com.snackbar.pedidos.application.usecases.IniciarPagamentoTotemPixUseCase;
import com.snackbar.pedidos.infrastructure.config.PagamentoTotemProperties;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/pagamentos-totem")
@RequiredArgsConstructor
public class PagamentoTotemRestController {

    private final IniciarPagamentoTotemCartaoUseCase iniciarCartaoUseCase;
    private final ConfirmarPagamentoTotemCartaoUseCase confirmarCartaoUseCase;
    private final IniciarPagamentoTotemPixUseCase iniciarPixUseCase;
    private final BuscarPagamentoTotemUseCase buscarPagamentoUseCase;
    private final CancelarPagamentoTotemUseCase cancelarPagamentoUseCase;
    private final PagamentoTotemProperties properties;

    @PostMapping("/cartao/iniciar")
    public ResponseEntity<PagamentoTotemDTO> iniciarCartao(
            @Valid @RequestBody IniciarPagamentoTotemCartaoRequest request) {
        exigirFeatureHabilitada();
        return ResponseEntity.status(HttpStatus.CREATED).body(iniciarCartaoUseCase.executar(request));
    }

    @PostMapping("/cartao/confirmar")
    public ResponseEntity<PagamentoTotemDTO> confirmarCartao(
            @Valid @RequestBody ConfirmarPagamentoTotemCartaoRequest request) {
        exigirFeatureHabilitada();
        return ResponseEntity.ok(confirmarCartaoUseCase.executar(request));
    }

    @PostMapping("/pix/iniciar")
    public ResponseEntity<PixCobrancaCriadaDTO> iniciarPix(
            @Valid @RequestBody IniciarPagamentoTotemPixRequest request) {
        exigirFeatureHabilitada();
        return ResponseEntity.status(HttpStatus.CREATED).body(iniciarPixUseCase.executar(request));
    }

    @GetMapping("/{correlationId}")
    public ResponseEntity<PagamentoTotemDTO> buscarStatus(@PathVariable String correlationId) {
        exigirFeatureHabilitada();
        return ResponseEntity.ok(buscarPagamentoUseCase.executar(correlationId));
    }

    @PostMapping("/cancelar")
    public ResponseEntity<PagamentoTotemDTO> cancelar(
            @Valid @RequestBody CancelarPagamentoTotemRequest request) {
        exigirFeatureHabilitada();
        return ResponseEntity.ok(cancelarPagamentoUseCase.executar(request));
    }

    private void exigirFeatureHabilitada() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
