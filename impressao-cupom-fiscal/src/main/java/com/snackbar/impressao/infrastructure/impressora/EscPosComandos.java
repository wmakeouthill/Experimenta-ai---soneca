package com.snackbar.impressao.infrastructure.impressora;

public class EscPosComandos {
    public static final byte ESC = 0x1B;
    public static final byte GS = 0x1D;
    public static final byte LF = 0x0A;
    public static final byte DLE = 0x10;
    public static final byte EOT = 0x04;
    
    public static byte[] inicializar() {
        return new byte[]{ESC, '@'};
    }
    
    public static byte[] cortarPapel() {
        return new byte[]{GS, 'V', 66, 0};
    }
    
    public static byte[] cortarPapelParcial() {
        return new byte[]{GS, 'V', 65, 0};
    }
    
    public static byte[] alinharEsquerda() {
        return new byte[]{ESC, 'a', 0};
    }
    
    public static byte[] alinharCentro() {
        return new byte[]{ESC, 'a', 1};
    }
    
    public static byte[] alinharDireita() {
        return new byte[]{ESC, 'a', 2};
    }
    
    public static byte[] textoNormal() {
        return new byte[]{ESC, '!', 0};
    }
    
    public static byte[] textoNegrito() {
        return new byte[]{ESC, '!', 8};
    }
    
    public static byte[] textoGrande() {
        return new byte[]{ESC, '!', 16};
    }
    
    public static byte[] textoDuploAltura() {
        return new byte[]{ESC, '!', 32};
    }
    
    public static byte[] textoDuploLargura() {
        return new byte[]{ESC, '!', 16};
    }
    
    public static byte[] textoDuplo() {
        return new byte[]{ESC, '!', 48};
    }
    
    public static byte[] linhaSeparadora(int largura) {
        StringBuilder linha = new StringBuilder();
        for (int i = 0; i < largura; i++) {
            linha.append("-");
        }
        return (linha.toString() + "\n").getBytes();
    }
    
    public static byte[] linhaEmBranco(int quantidade) {
        StringBuilder linhas = new StringBuilder();
        for (int i = 0; i < quantidade; i++) {
            linhas.append("\n");
        }
        return linhas.toString().getBytes();
    }
    
    public static byte[] abrirGaveta() {
        return new byte[]{ESC, 'p', 0, 60, 120};
    }
}

