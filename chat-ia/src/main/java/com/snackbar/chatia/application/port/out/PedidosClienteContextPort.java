package com.snackbar.chatia.application.port.out;

import com.snackbar.chatia.application.dto.HistoricoPedidosClienteContextDTO;

/**
 * Porta de saída para obter histórico de pedidos do cliente.
 */
public interface PedidosClienteContextPort {
    
    /**
     * Busca histórico de pedidos do cliente para contexto da IA.
     * 
     * @param clienteId ID do cliente
     * @return histórico de pedidos resumido
     */
    HistoricoPedidosClienteContextDTO buscarHistoricoPedidosCliente(String clienteId);
    
    /**
     * Busca produtos mais pedidos pelo cliente.
     * 
     * @param clienteId ID do cliente
     * @param limite quantidade máxima de produtos
     * @return lista de produtos favoritos
     */
    HistoricoPedidosClienteContextDTO buscarProdutosFavoritosCliente(String clienteId, int limite);
}
