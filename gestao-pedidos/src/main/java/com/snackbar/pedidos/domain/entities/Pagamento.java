package com.snackbar.pedidos.domain.entities;

import java.time.LocalDateTime;
import java.util.EnumSet;

import com.snackbar.kernel.domain.entities.BaseEntity;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.domain.valueobjects.DadosPix;
import com.snackbar.pedidos.domain.valueobjects.DadosTef;

import lombok.Getter;

/**
 * Pagamento digital/integrado de um pedido (canal TOTEM ou MESA),
 * processado por um gateway (SIMULADO, STONE, GETNET).
 *
 * Referencia exatamente UMA origem: pedidoId (pedido real),
 * pedidoPendenteId (pedido de mesa pre-pago aguardando aceite)
 * ou contaMesaId (conta pos-paga de mesa).
 */
@Getter
public class Pagamento extends BaseEntity {

    private static final EnumSet<StatusPagamento> STATUS_FINAIS = EnumSet.of(
            StatusPagamento.APROVADO,
            StatusPagamento.NEGADO,
            StatusPagamento.CANCELADO,
            StatusPagamento.FALHA_TECNICA,
            StatusPagamento.EXPIRADO);

    private CanalPagamento canal;
    private GatewayPagamento gateway;
    private String pedidoId;
    private String pedidoPendenteId;
    private String contaMesaId;
    private String correlationId;
    private String gatewayPaymentId;
    private long valorCentavos;
    private MeioPagamentoGateway meioPagamento;
    private StatusPagamento status;
    private String nsuTef;
    private String bandeira;
    private String codigoAutorizacao;
    private String codigoAdquirente;
    private String comprovanteCliente;
    private String pixTxid;
    private String pixQrCodePayload;
    private String pixQrCodeBase64;
    private String pixCopiaECola;
    private String pixEndToEndId;
    private LocalDateTime pixExpiracaoEm;
    private String motivo;
    private LocalDateTime iniciadoEm;
    private LocalDateTime finalizadoEm;
    private Long version;

    private Pagamento() {
        super();
    }

    public static Pagamento iniciarParaPedido(
            String pedidoId,
            CanalPagamento canal,
            GatewayPagamento gateway,
            long valorCentavos,
            MeioPagamentoGateway meioPagamento,
            String correlationId) {
        validarObrigatorio(pedidoId, "pedidoId");
        Pagamento pagamento = iniciar(canal, gateway, valorCentavos, meioPagamento, correlationId);
        pagamento.pedidoId = pedidoId.trim();
        return pagamento;
    }

    public static Pagamento iniciarParaPedidoPendente(
            String pedidoPendenteId,
            CanalPagamento canal,
            GatewayPagamento gateway,
            long valorCentavos,
            MeioPagamentoGateway meioPagamento,
            String correlationId) {
        validarObrigatorio(pedidoPendenteId, "pedidoPendenteId");
        Pagamento pagamento = iniciar(canal, gateway, valorCentavos, meioPagamento, correlationId);
        pagamento.pedidoPendenteId = pedidoPendenteId.trim();
        return pagamento;
    }

    public static Pagamento iniciarParaContaMesa(
            String contaMesaId,
            CanalPagamento canal,
            GatewayPagamento gateway,
            long valorCentavos,
            MeioPagamentoGateway meioPagamento,
            String correlationId) {
        validarObrigatorio(contaMesaId, "contaMesaId");
        Pagamento pagamento = iniciar(canal, gateway, valorCentavos, meioPagamento, correlationId);
        pagamento.contaMesaId = contaMesaId.trim();
        return pagamento;
    }

    private static Pagamento iniciar(
            CanalPagamento canal,
            GatewayPagamento gateway,
            long valorCentavos,
            MeioPagamentoGateway meioPagamento,
            String correlationId) {
        validarObrigatorio(correlationId, "correlationId");
        if (canal == null) {
            throw new ValidationException("canal do pagamento e obrigatorio");
        }
        if (gateway == null) {
            throw new ValidationException("gateway do pagamento e obrigatorio");
        }
        if (valorCentavos <= 0) {
            throw new ValidationException("valor do pagamento deve ser maior que zero");
        }
        if (meioPagamento == null) {
            throw new ValidationException("meio de pagamento e obrigatorio");
        }

        Pagamento pagamento = new Pagamento();
        pagamento.canal = canal;
        pagamento.gateway = gateway;
        pagamento.valorCentavos = valorCentavos;
        pagamento.meioPagamento = meioPagamento;
        pagamento.correlationId = correlationId.trim();
        pagamento.status = StatusPagamento.INICIADO;
        pagamento.iniciadoEm = LocalDateTime.now();
        pagamento.touch();
        return pagamento;
    }

    public static Pagamento restaurar(
            String id,
            CanalPagamento canal,
            GatewayPagamento gateway,
            String pedidoId,
            String pedidoPendenteId,
            String contaMesaId,
            String correlationId,
            String gatewayPaymentId,
            long valorCentavos,
            MeioPagamentoGateway meioPagamento,
            StatusPagamento status,
            String nsuTef,
            String bandeira,
            String codigoAutorizacao,
            String codigoAdquirente,
            String comprovanteCliente,
            String pixTxid,
            String pixQrCodePayload,
            String pixQrCodeBase64,
            String pixCopiaECola,
            String pixEndToEndId,
            LocalDateTime pixExpiracaoEm,
            String motivo,
            LocalDateTime iniciadoEm,
            LocalDateTime finalizadoEm,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long version) {

        Pagamento pagamento = new Pagamento();
        pagamento.restaurarId(id);
        pagamento.restaurarTimestamps(createdAt, updatedAt);
        pagamento.canal = canal;
        pagamento.gateway = gateway;
        pagamento.pedidoId = pedidoId;
        pagamento.pedidoPendenteId = pedidoPendenteId;
        pagamento.contaMesaId = contaMesaId;
        pagamento.correlationId = correlationId;
        pagamento.gatewayPaymentId = gatewayPaymentId;
        pagamento.valorCentavos = valorCentavos;
        pagamento.meioPagamento = meioPagamento;
        pagamento.status = status;
        pagamento.nsuTef = nsuTef;
        pagamento.bandeira = bandeira;
        pagamento.codigoAutorizacao = codigoAutorizacao;
        pagamento.codigoAdquirente = codigoAdquirente;
        pagamento.comprovanteCliente = comprovanteCliente;
        pagamento.pixTxid = pixTxid;
        pagamento.pixQrCodePayload = pixQrCodePayload;
        pagamento.pixQrCodeBase64 = pixQrCodeBase64;
        pagamento.pixCopiaECola = pixCopiaECola;
        pagamento.pixEndToEndId = pixEndToEndId;
        pagamento.pixExpiracaoEm = pixExpiracaoEm;
        pagamento.motivo = motivo;
        pagamento.iniciadoEm = iniciadoEm;
        pagamento.finalizadoEm = finalizadoEm;
        pagamento.version = version;
        return pagamento;
    }

    public void definirGatewayPaymentId(String gatewayPaymentId) {
        validarObrigatorio(gatewayPaymentId, "gatewayPaymentId");
        this.gatewayPaymentId = gatewayPaymentId.trim();
        touch();
    }

    public void marcarAguardandoTef() {
        exigirStatus(StatusPagamento.INICIADO);
        if (!meioPagamento.isCartao()) {
            throw new ValidationException("apenas cartao pode aguardar TEF");
        }
        this.status = StatusPagamento.AGUARDANDO_TEF;
        touch();
    }

    public void marcarAguardandoPix(DadosPix dadosPix) {
        exigirStatus(StatusPagamento.INICIADO);
        if (meioPagamento != MeioPagamentoGateway.PIX) {
            throw new ValidationException("apenas PIX pode aguardar QR Code");
        }
        this.pixTxid = dadosPix.txid();
        this.pixQrCodePayload = dadosPix.qrCodePayload();
        this.pixQrCodeBase64 = dadosPix.qrCodeBase64();
        this.pixCopiaECola = dadosPix.copiaECola();
        this.pixExpiracaoEm = dadosPix.expiracaoEm();
        this.status = StatusPagamento.AGUARDANDO_PIX;
        touch();
    }

    public void aprovarTef(DadosTef dadosTef) {
        exigirStatus(StatusPagamento.AGUARDANDO_TEF);
        this.nsuTef = dadosTef.nsu();
        this.bandeira = dadosTef.bandeira();
        this.codigoAutorizacao = dadosTef.codigoAutorizacao();
        this.codigoAdquirente = dadosTef.codigoAdquirente();
        this.comprovanteCliente = dadosTef.comprovanteCliente();
        finalizar(StatusPagamento.APROVADO, null);
    }

    public void aprovarPix(String endToEndId) {
        exigirStatus(StatusPagamento.AGUARDANDO_PIX);
        validarObrigatorio(endToEndId, "endToEndId");
        this.pixEndToEndId = endToEndId.trim();
        finalizar(StatusPagamento.APROVADO, null);
    }

    public void negar(String motivo) {
        exigirNaoFinalizado();
        finalizar(StatusPagamento.NEGADO, motivo);
    }

    public void cancelar(String motivo) {
        exigirNaoFinalizado();
        finalizar(StatusPagamento.CANCELADO, motivo);
    }

    public void marcarFalhaTecnica(String motivo) {
        exigirNaoFinalizado();
        finalizar(StatusPagamento.FALHA_TECNICA, motivo);
    }

    public void expirar() {
        exigirNaoFinalizado();
        finalizar(StatusPagamento.EXPIRADO, "Tempo de pagamento expirado");
    }

    public boolean estaFinalizado() {
        return STATUS_FINAIS.contains(status);
    }

    private void finalizar(StatusPagamento novoStatus, String motivoFinalizacao) {
        this.status = novoStatus;
        this.motivo = normalizarMotivo(motivoFinalizacao);
        this.finalizadoEm = LocalDateTime.now();
        touch();
    }

    private void exigirStatus(StatusPagamento statusEsperado) {
        if (this.status != statusEsperado) {
            throw new ValidationException(
                    "pagamento em status " + this.status + " nao permite esta operacao");
        }
    }

    private void exigirNaoFinalizado() {
        if (estaFinalizado()) {
            throw new ValidationException("pagamento ja finalizado");
        }
    }

    private static void validarObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ValidationException(campo + " e obrigatorio");
        }
    }

    private static String normalizarMotivo(String motivo) {
        return motivo == null || motivo.isBlank() ? null : motivo.trim();
    }
}
