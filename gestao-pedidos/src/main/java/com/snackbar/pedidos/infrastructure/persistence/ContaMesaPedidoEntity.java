package com.snackbar.pedidos.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "conta_mesa_pedidos")
@IdClass(ContaMesaPedidoEntity.PK.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContaMesaPedidoEntity {

    @Id
    @Column(name = "conta_mesa_id", length = 36)
    private String contaMesaId;

    @Id
    @Column(name = "pedido_id", length = 36)
    private String pedidoId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private String contaMesaId;
        private String pedidoId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(contaMesaId, pk.contaMesaId)
                    && Objects.equals(pedidoId, pk.pedidoId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(contaMesaId, pedidoId);
        }
    }
}
