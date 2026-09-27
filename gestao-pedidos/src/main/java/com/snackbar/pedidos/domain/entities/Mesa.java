package com.snackbar.pedidos.domain.entities;

import com.snackbar.kernel.domain.entities.BaseEntity;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.domain.valueobjects.QrCodeToken;
import lombok.Getter;

/**
 * Entidade de domínio Mesa.
 * Representa uma mesa física do estabelecimento com QR Code único.
 */
@Getter
public class Mesa extends BaseEntity {

    private int numero;
    private String nome;
    private QrCodeToken qrCodeToken;
    private boolean ativa;
    private Piso piso;

    private Mesa() {
        super();
        this.ativa = true;
        this.piso = Piso.TERREO;
    }

    /**
     * Factory method para criar uma nova mesa.
     */
    public static Mesa criar(int numero, String nome) {
        validarDados(numero, nome);

        Mesa mesa = new Mesa();
        mesa.numero = numero;
        mesa.nome = nome.trim();
        mesa.qrCodeToken = QrCodeToken.gerar();
        mesa.touch();
        return mesa;
    }

    /**
     * Restaura uma mesa do banco de dados.
     */
    public static Mesa restaurar(String id, int numero, String nome, String qrCodeToken, boolean ativa, Piso piso,
            java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt) {
        Mesa mesa = new Mesa();
        mesa.restaurarId(id);
        mesa.numero = numero;
        mesa.nome = nome;
        mesa.qrCodeToken = QrCodeToken.restaurar(qrCodeToken);
        mesa.ativa = ativa;
        mesa.piso = piso;
        mesa.restaurarTimestamps(createdAt, updatedAt);
        return mesa;
    }

    /**
     * Atualiza o nome da mesa.
     */
    public void atualizarNome(String novoNome) {
        if (novoNome == null || novoNome.trim().isEmpty()) {
            throw new ValidationException("Nome da mesa não pode ser vazio");
        }
        this.nome = novoNome.trim();
        touch();
    }

    /**
     * Atualiza o número da mesa.
     */
    public void atualizarNumero(int novoNumero) {
        if (novoNumero <= 0) {
            throw new ValidationException("Número da mesa deve ser maior que zero");
        }
        this.numero = novoNumero;
        touch();
    }

    /**
     * Define o piso da mesa; os pedidos da mesa aparecem no painel desse piso.
     */
    public void atualizarPiso(Piso novoPiso) {
        if (novoPiso == null) {
            throw new ValidationException("Piso da mesa é obrigatório");
        }
        this.piso = novoPiso;
        touch();
    }

    /**
     * Ativa a mesa.
     */
    public void ativar() {
        this.ativa = true;
        touch();
    }

    /**
     * Desativa a mesa.
     */
    public void desativar() {
        this.ativa = false;
        touch();
    }

    /**
     * Regenera o token do QR Code (útil em caso de vazamento).
     */
    public void regenerarQrCode() {
        this.qrCodeToken = QrCodeToken.gerar();
        touch();
    }

    /**
     * Retorna o valor do token do QR Code.
     */
    public String getQrCodeTokenValor() {
        return qrCodeToken.getValor();
    }

    private static void validarDados(int numero, String nome) {
        if (numero <= 0) {
            throw new ValidationException("Número da mesa deve ser maior que zero");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new ValidationException("Nome da mesa não pode ser vazio");
        }
        if (nome.length() > 100) {
            throw new ValidationException("Nome da mesa não pode ter mais de 100 caracteres");
        }
    }
}
