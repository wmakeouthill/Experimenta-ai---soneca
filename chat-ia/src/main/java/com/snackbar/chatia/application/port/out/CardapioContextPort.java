package com.snackbar.chatia.application.port.out;

import com.snackbar.chatia.application.dto.CardapioContextDTO;

/**
 * Porta de saída para obter contexto do cardápio para o Chat IA.
 */
public interface CardapioContextPort {
    
    /**
     * Busca o cardápio completo com categorias e produtos disponíveis.
     * 
     * @return contexto do cardápio formatado para IA
     */
    CardapioContextDTO buscarCardapioParaIA();
}
