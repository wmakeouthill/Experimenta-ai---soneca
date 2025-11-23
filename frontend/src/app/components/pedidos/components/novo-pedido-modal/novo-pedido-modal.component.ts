import { Component, inject, input, output, signal, computed, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { SelecaoClienteComponent } from '../selecao-cliente/selecao-cliente.component';
import { SelecaoProdutosComponent } from '../selecao-produtos/selecao-produtos.component';
import { ItensPedidoComponent } from '../itens-pedido/itens-pedido.component';
import { MeiosPagamentoComponent } from '../meios-pagamento/meios-pagamento.component';
import { Cliente } from '../../../../services/cliente.service';
import { Produto } from '../../../../services/produto.service';
import { ItemPedidoRequest, MeioPagamentoPedido } from '../../../../services/pedido.service';

@Component({
  selector: 'app-novo-pedido-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    SelecaoClienteComponent,
    SelecaoProdutosComponent,
    ItensPedidoComponent,
    MeiosPagamentoComponent
  ],
  templateUrl: './novo-pedido-modal.component.html',
  styleUrl: './novo-pedido-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NovoPedidoModalComponent {
  private readonly fb = inject(FormBuilder);

  readonly aberto = input.required<boolean>();
  readonly produtos = input.required<Produto[]>();
  readonly onFechar = output<void>();
  readonly onCriarPedido = output<{
    clienteId: string;
    clienteNome: string;
    itens: ItemPedidoRequest[];
    meiosPagamento: MeioPagamentoPedido[];
    observacoes?: string;
  }>();

  readonly clienteSelecionado = signal<Cliente | null>(null);
  readonly itensSelecionados = signal<ItemPedidoRequest[]>([]);
  readonly meiosPagamento = signal<MeioPagamentoPedido[]>([]);

  readonly pedidoForm: FormGroup;

  readonly valorTotal = computed(() => {
    return this.itensSelecionados().reduce((total, item) => {
      const produto = this.produtos().find(p => p.id === item.produtoId);
      if (produto) {
        return total + (produto.preco * item.quantidade);
      }
      return total;
    }, 0);
  });

  readonly valorRestante = computed(() => {
    const total = this.valorTotal();
    const totalMeiosPagamento = this.meiosPagamento().reduce((sum, mp) => sum + mp.valor, 0);
    return total - totalMeiosPagamento;
  });

  constructor() {
    this.pedidoForm = this.fb.group({
      observacoes: ['']
    });
  }

  onClienteSelecionado(cliente: Cliente): void {
    this.clienteSelecionado.set(cliente);
  }

  onClienteCriado(cliente: Cliente): void {
    this.clienteSelecionado.set(cliente);
  }

  onTrocarCliente(): void {
    this.clienteSelecionado.set(null);
  }

  onProdutoSelecionado(produto: Produto): void {
    const itemExistente = this.itensSelecionados().find(i => i.produtoId === produto.id);
    
    if (itemExistente) {
      itemExistente.quantidade += 1;
      this.itensSelecionados.update(lista => [...lista]);
    } else {
      const novoItem: ItemPedidoRequest = {
        produtoId: produto.id,
        quantidade: 1,
        observacoes: ''
      };
      (novoItem as any).itemId = `${produto.id}-${Date.now()}-${Math.random()}`;
      this.itensSelecionados.update(lista => [...lista, novoItem]);
    }
  }

  onQuantidadeAlterada(event: { index: number; quantidade: number }): void {
    const itens = this.itensSelecionados();
    itens[event.index].quantidade = event.quantidade;
    this.itensSelecionados.set([...itens]);
  }

  onItemRemovido(index: number): void {
    this.itensSelecionados.update(lista => lista.filter((_, i) => i !== index));
  }

  onMeiosPagamentoChange(meiosPagamento: MeioPagamentoPedido[]): void {
    this.meiosPagamento.set(meiosPagamento);
  }

  fechar(): void {
    this.onFechar.emit();
    this.resetar();
  }

  criarPedido(): void {
    const cliente = this.clienteSelecionado();
    if (!cliente || this.itensSelecionados().length === 0 || this.meiosPagamento().length === 0) {
      return;
    }

    const request: {
      clienteId: string;
      clienteNome: string;
      itens: any[];
      meiosPagamento: MeioPagamentoPedido[];
      observacoes?: string;
    } = {
      clienteId: cliente.id,
      clienteNome: cliente.nome,
      itens: this.itensSelecionados().map(item => {
        const itemRequest: any = {
          produtoId: item.produtoId,
          quantidade: item.quantidade
        };
        if (item.observacoes?.trim()) {
          itemRequest.observacoes = item.observacoes.trim();
        }
        return itemRequest;
      }),
      meiosPagamento: this.meiosPagamento()
    };

    if (this.pedidoForm.value.observacoes?.trim()) {
      request.observacoes = this.pedidoForm.value.observacoes.trim();
    }

    this.onCriarPedido.emit(request);
    this.resetar();
  }

  private resetar(): void {
    this.clienteSelecionado.set(null);
    this.itensSelecionados.set([]);
    this.meiosPagamento.set([]);
    this.pedidoForm.reset();
  }

  podeCriarPedido(): boolean {
    return !!this.clienteSelecionado() && 
           this.itensSelecionados().length > 0 && 
           this.meiosPagamento().length > 0 && 
           this.valorRestante() <= 0.01;
  }
}

