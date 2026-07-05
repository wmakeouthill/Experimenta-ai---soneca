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

import com.snackbar.pedidos.application.dto.CancelarPagamentoRequest;
import com.snackbar.pedidos.application.dto.ConfirmarPagamentoCartaoPresencialRequest;
import com.snackbar.pedidos.application.dto.IniciarPagamentoCartaoPresencialRequest;
import com.snackbar.pedidos.application.dto.IniciarPagamentoPixRequest;
import com.snackbar.pedidos.application.dto.PagamentoDTO;
import com.snackbar.pedidos.application.dto.PixCobrancaCriadaDTO;
import com.snackbar.pedidos.application.usecases.BuscarPagamentoUseCase;
import com.snackbar.pedidos.application.usecases.CancelarPagamentoUseCase;
import com.snackbar.pedidos.application.usecases.ConfirmarPagamentoCartaoPresencialUseCase;
import com.snackbar.pedidos.application.usecases.IniciarPagamentoCartaoPresencialUseCase;
import com.snackbar.pedidos.application.usecases.IniciarPagamentoPixUseCase;
import com.snackbar.pedidos.infrastructure.config.PagamentoProperties;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/pagamentos")
@RequiredArgsConstructor
public class PagamentoRestController {

    private final IniciarPagamentoCartaoPresencialUseCase iniciarCartaoUseCase;
    private final ConfirmarPagamentoCartaoPresencialUseCase confirmarCartaoUseCase;
    private final IniciarPagamentoPixUseCase iniciarPixUseCase;
    private final BuscarPagamentoUseCase buscarPagamentoUseCase;
    private final CancelarPagamentoUseCase cancelarPagamentoUseCase;
    private final PagamentoProperties properties;

    @PostMapping("/cartao-presencial/iniciar")
    public ResponseEntity<PagamentoDTO> iniciarCartao(
            @Valid @RequestBody IniciarPagamentoCartaoPresencialRequest request) {
        exigirFeatureHabilitada();
        return ResponseEntity.status(HttpStatus.CREATED).body(iniciarCartaoUseCase.executar(request));
    }

    @PostMapping("/cartao-presencial/confirmar")
    public ResponseEntity<PagamentoDTO> confirmarCartao(
            @Valid @RequestBody ConfirmarPagamentoCartaoPresencialRequest request) {
        exigirFeatureHabilitada();
        return ResponseEntity.ok(confirmarCartaoUseCase.executar(request));
    }

    @PostMapping("/pix/iniciar")
    public ResponseEntity<PixCobrancaCriadaDTO> iniciarPix(
            @Valid @RequestBody IniciarPagamentoPixRequest request) {
        exigirFeatureHabilitada();
        return ResponseEntity.status(HttpStatus.CREATED).body(iniciarPixUseCase.executar(request));
    }

    @GetMapping("/{correlationId}")
    public ResponseEntity<PagamentoDTO> buscarStatus(@PathVariable String correlationId) {
        exigirFeatureHabilitada();
        return ResponseEntity.ok(buscarPagamentoUseCase.executar(correlationId));
    }

    @PostMapping("/cancelar")
    public ResponseEntity<PagamentoDTO> cancelar(
            @Valid @RequestBody CancelarPagamentoRequest request) {
        exigirFeatureHabilitada();
        return ResponseEntity.ok(cancelarPagamentoUseCase.executar(request));
    }

    private void exigirFeatureHabilitada() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
