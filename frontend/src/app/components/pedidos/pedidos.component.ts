import { Component, inject, PLATFORM_ID, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { usePedidos } from './composables/use-pedidos';
import { PedidoService, StatusPedido, Pedido } from '../../services/pedido.service';
import { NovoPedidoModalComponent } from './components/novo-pedido-modal/novo-pedido-modal.component';
import { MenuContextoPedidoComponent } from './components/menu-contexto-pedido/menu-contexto-pedido.component';

@Component({
  selector: 'app-pedidos',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    FormsModule,
    NovoPedidoModalComponent,
    MenuContextoPedidoComponent
  ],
  templateUrl: './pedidos.component.html',
  styleUrl: './pedidos.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PedidosComponent implements OnInit {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);
  private readonly pedidoService = inject(PedidoService);

  // Composable com toda a lógica de pedidos - inicializado no construtor para contexto de injeção válido
  readonly pedidosComposable!: ReturnType<typeof usePedidos>;

  // Expor propriedades necessárias (usando getters para acessar após inicialização)
  get pedidosFiltrados() {
    return this.pedidosComposable.pedidosFiltrados;
  }

  get pedidosPorStatus() {
    return this.pedidosComposable.pedidosPorStatus;
  }

  get estado() {
    return this.pedidosComposable.estado;
  }

  get erro() {
    return this.pedidosComposable.erro;
  }

  get estaCarregando() {
    return this.pedidosComposable.estaCarregando;
  }

  get temPedidos() {
    return this.pedidosComposable.temPedidos;
  }

  get pesquisaTexto() {
    return this.pedidosComposable.pesquisaTexto;
  }

  get produtos() {
    return this.pedidosComposable.produtos;
  }

  readonly StatusPedido = StatusPedido;

  // Modal/Formulário states
  readonly mostrarFormulario = signal(false);

  // Menu de contexto
  readonly menuContexto = signal<{ pedidoId: string; x: number; y: number } | null>(null);

  constructor() {
    this.pedidosComposable = usePedidos();
  }

  ngOnInit(): void {
    if (this.isBrowser) {
      this.carregarDados();
      document.addEventListener('click', () => this.fecharMenuContexto());
      document.addEventListener('contextmenu', (e) => {
        const target = e.target as HTMLElement;
        if (!target.closest('.pedido-card')) {
          this.fecharMenuContexto();
        }
      });
    }
  }

  private carregarDados(): void {
    this.pedidosComposable.carregarPedidos();
    this.pedidosComposable.carregarProdutos();
  }

  filtrarPorStatus(status: StatusPedido): void {
    this.pedidosComposable.filtrarPorStatus(status);
  }

  pesquisar(texto: string): void {
    this.pedidosComposable.pesquisar(texto);
  }

  limparFiltros(): void {
    this.pedidosComposable.limparFiltros();
  }

  recarregar(): void {
    this.carregarDados();
  }

  abrirFormulario(): void {
    this.mostrarFormulario.set(true);
  }

  fecharFormulario(): void {
    this.mostrarFormulario.set(false);
  }

  onPedidoCriado(request: {
    clienteId: string;
    clienteNome: string;
    itens: any[];
    meiosPagamento: any[];
    observacoes?: string;
  }): void {
    this.pedidoService.criar(request)
      .subscribe({
        next: (pedidoCriado) => {
          this.pedidosComposable.atualizarPedidoNoSignal(pedidoCriado);
          setTimeout(() => {
            this.pedidosComposable.carregarPedidos();
          }, 0);
          this.fecharFormulario();
        },
        error: (error) => {
          console.error('Erro ao criar pedido:', error);
          let mensagem = 'Erro ao criar pedido. Verifique se todos os campos estão preenchidos corretamente.';

          if (error.error?.message) {
            mensagem = error.error.message;
          } else if (error.error?.errors) {
            const erros = Object.values(error.error.errors).join(', ');
            mensagem = `Erro de validação: ${erros}`;
          } else if (error.message) {
            mensagem = error.message;
          }

          if (this.isBrowser) {
            alert(mensagem);
          }
        }
      });
  }

  atualizarStatus(pedidoId: string, novoStatus: StatusPedido): void {
    this.pedidoService.atualizarStatus(pedidoId, novoStatus)
      .subscribe({
        next: (pedidoAtualizado) => {
          // Atualiza o pedido no signal imediatamente para atualizar os contadores em tempo real
          this.pedidosComposable.atualizarPedidoNoSignal(pedidoAtualizado);
          // Recarrega todos os pedidos de forma assíncrona para garantir sincronização completa
          // Usa setTimeout para garantir que a atualização imediata seja processada primeiro
          setTimeout(() => {
            this.pedidosComposable.carregarPedidos();
          }, 0);
        },
        error: (error) => {
          console.error('Erro ao atualizar status:', error);
          let mensagem = 'Erro ao atualizar status. Tente novamente.';

          if (error.error) {
            if (error.error.message) {
              mensagem = error.error.message;
            } else if (error.error.errors) {
              const erros = Object.values(error.error.errors).join(', ');
              mensagem = `Erro de validação: ${erros}`;
            }
          } else if (error.message) {
            mensagem = error.message;
          }

          if (this.isBrowser) {
            alert(mensagem);
          }
        }
      });
  }

  cancelarPedido(pedidoId: string): void {
    if (!this.isBrowser || !confirm('Tem certeza que deseja cancelar este pedido?')) {
      return;
    }

    this.pedidoService.cancelar(pedidoId)
      .subscribe({
        next: (pedidoCancelado) => {
          // Atualiza o signal imediatamente para atualizar contadores em tempo real
          this.pedidosComposable.atualizarPedidoNoSignal(pedidoCancelado);
          // Recarrega todos os pedidos de forma assíncrona para garantir sincronização
          setTimeout(() => {
            this.pedidosComposable.carregarPedidos();
          }, 0);
        },
        error: (error) => {
          console.error('Erro ao cancelar pedido:', error);
          if (this.isBrowser) {
            alert('Erro ao cancelar pedido. Tente novamente.');
          }
        }
      });
  }


  // Menu de contexto
  abrirMenuContexto(event: MouseEvent, pedidoId: string): void {
    event.preventDefault();
    event.stopPropagation();

    this.menuContexto.set({
      pedidoId,
      x: event.clientX,
      y: event.clientY
    });
  }

  fecharMenuContexto(): void {
    this.menuContexto.set(null);
  }


  onStatusAlteradoViaMenu(event: { pedidoId: string; novoStatus: StatusPedido }): void {
    this.fecharMenuContexto();
    this.atualizarStatus(event.pedidoId, event.novoStatus);
  }

  obterPedidoDoMenu(): Pedido | null {
    const menu = this.menuContexto();
    if (!menu) return null;
    return this.pedidosFiltrados().find(p => p.id === menu.pedidoId) || null;
  }

  formatarData(data: string): string {
    return new Date(data).toLocaleString('pt-BR');
  }

  formatarPreco(preco: number): string {
    return `R$ ${preco.toFixed(2).replace('.', ',')}`;
  }
}

