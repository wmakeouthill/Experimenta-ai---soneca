package com.snackbar.pedidos.application.usecases;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.application.dto.PagamentoDTO;
import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.services.AplicarPagamentoMesaAprovadoService;
import com.snackbar.pedidos.domain.entities.MeioPagamento;
import com.snackbar.kernel.domain.exceptions.RecursoNaoEncontradoException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfirmarPagamentoPixUseCase {

    private final PagamentoRepositoryPort pagamentoRepository;
    private final AplicarPagamentoMesaAprovadoService aplicarPagamentoAprovado;

    @Transactional
    public PagamentoDTO executar(String txid, String endToEndId) {
        validarObrigatorio(txid, "txid");
        validarObrigatorio(endToEndId, "endToEndId");

        var pagamento = pagamentoRepository.buscarPorTxidPix(txid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento PIX nao encontrado"));

        if (pagamento.estaFinalizado()) {
            return PagamentoDTO.de(pagamento);
        }

        pagamento.aprovarPix(endToEndId);
        aplicarPagamentoAprovado.aplicar(pagamento, MeioPagamento.PIX);

        var salvo = pagamentoRepository.salvar(pagamento);
        log.info("Pagamento PIX confirmado correlationId={} txid={}",
                salvo.getCorrelationId(), salvo.getPixTxid());
        return PagamentoDTO.de(salvo);
    }

    private static void validarObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ValidationException(campo + " e obrigatorio");
        }
    }
}
