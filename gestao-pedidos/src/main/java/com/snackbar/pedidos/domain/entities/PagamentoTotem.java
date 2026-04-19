package com.snackbar.pedidos.domain.entities;

import java.time.LocalDateTime;
import java.util.EnumSet;

import com.snackbar.kernel.domain.entities.BaseEntity;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.domain.valueobjects.DadosPix;
import com.snackbar.pedidos.domain.valueobjects.DadosTef;

import lombok.Getter;

@Getter
public class PagamentoTotem extends BaseEntity {

    private static final EnumSet<StatusPagamentoTotem> STATUS_FINAIS = EnumSet.of(
            StatusPagamentoTotem.APROVADO,
            StatusPagamentoTotem.NEGADO,
            StatusPagamentoTotem.CANCELADO,
            StatusPagamentoTotem.FALHA_TECNICA,
            StatusPagamentoTotem.EXPIRADO);

    private String pedidoId;
    private String correlationId;
    private long valorCentavos;
    private MeioPagamentoTotem meioPagamento;
    private StatusPagamentoTotem status;
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

    private PagamentoTotem() {
        super();
    }

    public static PagamentoTotem iniciar(
            String pedidoId,
            long valorCentavos,
            MeioPagamentoTotem meioPagamento,
            String correlationId) {

        validarObrigatorio(pedidoId, "pedidoId");
        validarObrigatorio(correlationId, "correlationId");
        if (valorCentavos <= 0) {
            throw new ValidationException("valor do pagamento deve ser maior que zero");
        }
        if (meioPagamento == null) {
            throw new ValidationException("meio de pagamento e obrigatorio");
        }

        PagamentoTotem pagamento = new PagamentoTotem();
        pagamento.pedidoId = pedidoId.trim();
        pagamento.valorCentavos = valorCentavos;
        pagamento.meioPagamento = meioPagamento;
        pagamento.correlationId = correlationId.trim();
        pagamento.status = StatusPagamentoTotem.INICIADO;
        pagamento.iniciadoEm = LocalDateTime.now();
        pagamento.touch();
        return pagamento;
    }

    public static PagamentoTotem restaurar(
            String id,
            String pedidoId,
            String correlationId,
            long valorCentavos,
            MeioPagamentoTotem meioPagamento,
            StatusPagamentoTotem status,
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

        PagamentoTotem pagamento = new PagamentoTotem();
        pagamento.restaurarId(id);
        pagamento.restaurarTimestamps(createdAt, updatedAt);
        pagamento.pedidoId = pedidoId;
        pagamento.correlationId = correlationId;
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

    public void marcarAguardandoTef() {
        exigirStatus(StatusPagamentoTotem.INICIADO);
        if (!meioPagamento.isCartao()) {
            throw new ValidationException("apenas cartao pode aguardar TEF");
        }
        this.status = StatusPagamentoTotem.AGUARDANDO_TEF;
        touch();
    }

    public void marcarAguardandoPix(DadosPix dadosPix) {
        exigirStatus(StatusPagamentoTotem.INICIADO);
        if (meioPagamento != MeioPagamentoTotem.PIX) {
            throw new ValidationException("apenas PIX pode aguardar QR Code");
        }
        this.pixTxid = dadosPix.txid();
        this.pixQrCodePayload = dadosPix.qrCodePayload();
        this.pixQrCodeBase64 = dadosPix.qrCodeBase64();
        this.pixCopiaECola = dadosPix.copiaECola();
        this.pixExpiracaoEm = dadosPix.expiracaoEm();
        this.status = StatusPagamentoTotem.AGUARDANDO_PIX;
        touch();
    }

    public void aprovarTef(DadosTef dadosTef) {
        exigirStatus(StatusPagamentoTotem.AGUARDANDO_TEF);
        this.nsuTef = dadosTef.nsu();
        this.bandeira = dadosTef.bandeira();
        this.codigoAutorizacao = dadosTef.codigoAutorizacao();
        this.codigoAdquirente = dadosTef.codigoAdquirente();
        this.comprovanteCliente = dadosTef.comprovanteCliente();
        finalizar(StatusPagamentoTotem.APROVADO, null);
    }

    public void aprovarPix(String endToEndId) {
        exigirStatus(StatusPagamentoTotem.AGUARDANDO_PIX);
        validarObrigatorio(endToEndId, "endToEndId");
        this.pixEndToEndId = endToEndId.trim();
        finalizar(StatusPagamentoTotem.APROVADO, null);
    }

    public void negar(String motivo) {
        exigirNaoFinalizado();
        finalizar(StatusPagamentoTotem.NEGADO, motivo);
    }

    public void cancelar(String motivo) {
        exigirNaoFinalizado();
        finalizar(StatusPagamentoTotem.CANCELADO, motivo);
    }

    public void marcarFalhaTecnica(String motivo) {
        exigirNaoFinalizado();
        finalizar(StatusPagamentoTotem.FALHA_TECNICA, motivo);
    }

    public void expirar() {
        exigirNaoFinalizado();
        finalizar(StatusPagamentoTotem.EXPIRADO, "Tempo de pagamento expirado");
    }

    public boolean estaFinalizado() {
        return STATUS_FINAIS.contains(status);
    }

    private void finalizar(StatusPagamentoTotem novoStatus, String motivoFinalizacao) {
        this.status = novoStatus;
        this.motivo = normalizarMotivo(motivoFinalizacao);
        this.finalizadoEm = LocalDateTime.now();
        touch();
    }

    private void exigirStatus(StatusPagamentoTotem statusEsperado) {
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
