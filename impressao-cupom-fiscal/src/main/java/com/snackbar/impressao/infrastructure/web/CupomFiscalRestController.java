package com.snackbar.impressao.infrastructure.web;

import com.snackbar.impressao.application.dtos.ConfiguracaoImpressoraDTO;
import com.snackbar.impressao.application.dtos.ImprimirCupomRequest;
import com.snackbar.impressao.application.dtos.ImprimirCupomResponse;
import com.snackbar.impressao.application.dtos.SalvarConfiguracaoImpressoraRequest;
import com.snackbar.impressao.application.usecases.BuscarConfiguracaoImpressoraUseCase;
import com.snackbar.impressao.application.usecases.ImprimirCupomFiscalUseCase;
import com.snackbar.impressao.application.usecases.SalvarConfiguracaoImpressoraUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/impressao")
@RequiredArgsConstructor
public class CupomFiscalRestController {
    
    private final ImprimirCupomFiscalUseCase imprimirCupomFiscalUseCase;
    private final SalvarConfiguracaoImpressoraUseCase salvarConfiguracaoUseCase;
    private final BuscarConfiguracaoImpressoraUseCase buscarConfiguracaoUseCase;
    
    @PostMapping("/cupom-fiscal")
    public ResponseEntity<ImprimirCupomResponse> imprimirCupomFiscal(@Valid @RequestBody ImprimirCupomRequest request) {
        ImprimirCupomResponse response = imprimirCupomFiscalUseCase.executar(request);
        
        if (response.isSucesso()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/configuracao")
    public ResponseEntity<ConfiguracaoImpressoraDTO> buscarConfiguracao() {
        ConfiguracaoImpressoraDTO config = buscarConfiguracaoUseCase.executar();
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(config);
    }
    
    @PostMapping("/configuracao")
    public ResponseEntity<ConfiguracaoImpressoraDTO> salvarConfiguracao(@Valid @RequestBody SalvarConfiguracaoImpressoraRequest request) {
        ConfiguracaoImpressoraDTO config = salvarConfiguracaoUseCase.executar(request);
        return ResponseEntity.ok(config);
    }
}

