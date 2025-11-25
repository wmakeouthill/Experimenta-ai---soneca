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
public class EpsonTmT20ImpressoraAdapter extends BaseImpressoraAdapter {
    
    @Value("${impressao.epson.tm-t20.device:/dev/usb/lp0}")
    private String devicePath;
    
    @Value("${impressao.epson.tm-t20.modo-teste:true}")
    private boolean modoTeste;
    
    private String nomeArquivoTeste;
    
    @Override
    protected OutputStream obterOutputStream(CupomFiscal cupomFiscal) throws ImpressaoException {
        if (modoTeste) {
            nomeArquivoTeste = "cupom_epson_" + cupomFiscal.getPedido().getNumeroPedido() + ".prn";
            return criarOutputStreamArquivo(nomeArquivoTeste);
        }
        
        try {
            if (ConexaoImpressoraUtil.eConexaoRede(devicePath)) {
                return ConexaoImpressoraUtil.criarConexaoRede(devicePath);
            } else {
                return ConexaoImpressoraUtil.criarConexaoLocal(devicePath);
            }
        } catch (IOException e) {
            throw new ImpressaoException("Erro ao conectar com impressora EPSON TM-T20: " + e.getMessage(), e);
        }
    }
    
    @Override
    protected String obterNomeArquivoTeste(CupomFiscal cupomFiscal) {
        return nomeArquivoTeste;
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
        
        if (ConexaoImpressoraUtil.eConexaoRede(devicePath)) {
            return ConexaoImpressoraUtil.verificarConexaoRede(devicePath);
        } else {
            return ConexaoImpressoraUtil.verificarConexaoLocal(devicePath);
        }
    }
}

