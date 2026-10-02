package com.snackbar.pedidos.infrastructure.persistence;

import java.time.LocalDateTime;

public interface ReferenciaPosicaoPedido {
    String getId();

    String getSessaoId();

    LocalDateTime getDataPedido();

    String getNumeroPedido();
}
