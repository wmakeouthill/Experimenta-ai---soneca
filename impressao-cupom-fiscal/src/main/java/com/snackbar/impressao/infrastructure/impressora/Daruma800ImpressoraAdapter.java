package com.snackbar.impressao.infrastructure.impressora;

import com.snackbar.impressao.domain.entities.CupomFiscal;
import com.snackbar.impressao.domain.ports.ImpressaoException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;

@Component
@RequiredArgsConstructor
public class Daruma800ImpressoraAdapter extends BaseImpressoraAdapter {
    
    @Value("${impressao.daruma.800.device:/dev/usb/lp1}")
    private String devicePath;
    
    @Value("${impressao.daruma.800.modo-teste:true}")
    private boolean modoTeste;
    
    @Override
    protected OutputStream obterOutputStream(CupomFiscal cupomFiscal) throws ImpressaoException {
        if (modoTeste) {
            String nomeArquivo = "cupom_daruma_" + cupomFiscal.getPedido().getNumeroPedido() + ".prn";
            return criarOutputStreamArquivo(nomeArquivo);
        }
        
        try {
            return new java.io.FileOutputStream(devicePath);
        } catch (IOException e) {
            throw new ImpressaoException("Erro ao conectar com impressora DARUMA 800: " + e.getMessage(), e);
        }
    }
    
    @Override
    protected void fecharOutputStream(OutputStream outputStream) throws IOException {
        outputStream.close();
    }
    
    @Override
    protected boolean verificarImpressoraDisponivel() throws Exception {
        if (modoTeste) {
            return true;
        }
        
        return Paths.get(devicePath).toFile().exists();
    }
}

