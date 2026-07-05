package com.snackbar.pedidos.infrastructure.jobs;

import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.snackbar.pedidos.application.ports.PagamentoRepositoryPort;
import com.snackbar.pedidos.application.ports.PixGatewayPort;
import com.snackbar.pedidos.application.usecases.ConfirmarPagamentoPixUseCase;
import com.snackbar.pedidos.domain.entities.Pagamento;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fallback do webhook: consulta periodicamente os pagamentos PIX
 * aguardando confirmacao, confirma os pagos, expira QRs vencidos
 * e pagamentos pendentes antigos (INICIADO/AGUARDANDO_TEF/AGUARDANDO_PIX).
 */
@Component
@ConditionalOnProperty(name = "pagamento.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class MonitorPagamentosPixJob {

    private static final long MARGEM_EXPIRACAO_SEGUNDOS = 60;
    private static final long PENDENTES_TIMEOUT_MINUTOS = 30;

    private final PagamentoRepositoryPort pagamentoRepository;
    private final PixGatewayPort pixGateway;
    private final ConfirmarPagamentoPixUseCase confirmarPixUseCase;

    @Scheduled(fixedDelayString = "${pagamento.monitor.fixed-delay-ms:30000}")
    public void executar() {
        monitorarAguardandoPix();
        expirarPendentesAntigos();
    }

    private void monitorarAguardandoPix() {
        for (Pagamento pagamento : pagamentoRepository.buscarAguardandoPix()) {
            try {
                processar(pagamento);
            } catch (Exception exception) {
                log.warn("Falha ao monitorar pagamento PIX correlationId={}: {}",
                        pagamento.getCorrelationId(), exception.getMessage());
            }
        }
    }

    private void processar(Pagamento pagamento) {
        var resultado = pixGateway.consultarStatus(pagamento.getPixTxid());

        switch (resultado.status()) {
            case PAGA -> {
                String endToEndId = resultado.endToEndId() != null && !resultado.endToEndId().isBlank()
                        ? resultado.endToEndId()
                        : "POLL-" + pagamento.getPixTxid();
                confirmarPixUseCase.executar(pagamento.getPixTxid(), endToEndId);
                log.info("Pagamento PIX confirmado via polling correlationId={}",
                        pagamento.getCorrelationId());
            }
            case EXPIRADA -> expirar(pagamento);
            case CANCELADA -> {
                pagamento.cancelar("Cancelado no gateway");
                pagamentoRepository.salvar(pagamento);
            }
            case AGUARDANDO, DESCONHECIDO -> {
                if (qrVencido(pagamento)) {
                    expirar(pagamento);
                }
            }
        }
    }

    private boolean qrVencido(Pagamento pagamento) {
        return pagamento.getPixExpiracaoEm() != null
                && LocalDateTime.now().isAfter(
                        pagamento.getPixExpiracaoEm().plusSeconds(MARGEM_EXPIRACAO_SEGUNDOS));
    }

    private void expirar(Pagamento pagamento) {
        pagamento.expirar();
        pagamentoRepository.salvar(pagamento);
        log.info("Pagamento PIX expirado correlationId={}", pagamento.getCorrelationId());
    }

    private void expirarPendentesAntigos() {
        LocalDateTime limite = LocalDateTime.now().minusMinutes(PENDENTES_TIMEOUT_MINUTOS);
        for (Pagamento pagamento : pagamentoRepository.buscarPendentesAnteriores(limite)) {
            try {
                if (!pagamento.estaFinalizado()) {
                    pagamento.expirar();
                    pagamentoRepository.salvar(pagamento);
                    log.info("Pagamento pendente antigo expirado correlationId={}",
                            pagamento.getCorrelationId());
                }
            } catch (Exception exception) {
                log.warn("Falha ao expirar pagamento antigo correlationId={}: {}",
                        pagamento.getCorrelationId(), exception.getMessage());
            }
        }
    }
}
