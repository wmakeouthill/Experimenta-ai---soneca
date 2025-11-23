import { Component, input, output, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ItemPedidoRequest } from '../../../../services/pedido.service';
import { Produto } from '../../../../services/produto.service';

@Component({
  selector: 'app-itens-pedido',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './itens-pedido.component.html',
  styleUrl: './itens-pedido.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ItensPedidoComponent {
  readonly itens = input.required<ItemPedidoRequest[]>();
  readonly produtos = input.required<Produto[]>();
  readonly onQuantidadeAlterada = output<{ index: number; quantidade: number }>();
  readonly onItemRemovido = output<number>();

  buscarProdutoPorId(produtoId: string): Produto | undefined {
    return this.produtos().find(p => p.id === produtoId);
  }

  calcularSubtotalItem(item: ItemPedidoRequest): number {
    const produto = this.buscarProdutoPorId(item.produtoId);
    if (produto) {
      return produto.preco * item.quantidade;
    }
    return 0;
  }

  calcularTotal(): number {
    return this.itens().reduce((total, item) => {
      const produto = this.buscarProdutoPorId(item.produtoId);
      if (produto) {
        return total + (produto.preco * item.quantidade);
      }
      return total;
    }, 0);
  }

  obterItemId(item: ItemPedidoRequest): string {
    return (item as any).itemId || item.produtoId;
  }

  atualizarQuantidade(index: number, quantidade: number): void {
    if (quantidade <= 0) {
      this.onItemRemovido.emit(index);
    } else {
      this.onQuantidadeAlterada.emit({ index, quantidade });
    }
  }

  removerItem(index: number): void {
    this.onItemRemovido.emit(index);
  }

  formatarPreco(preco: number): string {
    return `R$ ${preco.toFixed(2).replace('.', ',')}`;
  }
}

