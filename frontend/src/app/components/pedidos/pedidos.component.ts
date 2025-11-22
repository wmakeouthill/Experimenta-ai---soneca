import { Component, inject, PLATFORM_ID, OnInit, signal, computed, effect, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { usePedidos } from './composables/use-pedidos';
import { PedidoService, StatusPedido, CriarPedidoRequest, ItemPedidoRequest, Pedido, MeioPagamento, MeioPagamentoPedido } from '../../services/pedido.service';
import { ClienteService, Cliente, CriarClienteRequest } from '../../services/cliente.service';
import { Produto } from '../../services/produto.service';

@Component({
  selector: 'app-pedidos',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    FormsModule,
    ReactiveFormsModule
  ],
  templateUrl: './pedidos.component.html',
  styleUrl: './pedidos.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PedidosComponent implements OnInit {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);
  private readonly pedidoService = inject(PedidoService);
  private readonly clienteService = inject(ClienteService);
  private readonly fb = inject(FormBuilder);

  // Composable com toda a lógica de pedidos
  readonly pedidosComposable = usePedidos();

  // Expor propriedades necessárias
  readonly pedidosFiltrados = this.pedidosComposable.pedidosFiltrados;
  readonly pedidosPorStatus = this.pedidosComposable.pedidosPorStatus;
  readonly estado = this.pedidosComposable.estado;
  readonly erro = this.pedidosComposable.erro;
  readonly estaCarregando = this.pedidosComposable.estaCarregando;
  readonly temPedidos = this.pedidosComposable.temPedidos;
  readonly pesquisaTexto = this.pedidosComposable.pesquisaTexto;
  readonly produtos = this.pedidosComposable.produtos;
  readonly pesquisaProduto = signal<string>('');
  readonly categoriaFiltro = signal<string | null>(null);

  // Modal/Formulário states
  readonly mostrarFormulario = signal(false);
  readonly clientes = signal<Cliente[]>([]);
  readonly clienteSelecionado = signal<Cliente | null>(null);
  readonly itensSelecionados = signal<ItemPedidoRequest[]>([]);
  readonly meiosPagamento = signal<MeioPagamentoPedido[]>([]);
  readonly meioPagamentoSelecionado = signal<MeioPagamento | null>(null);
  readonly valorMeioPagamento = signal<number>(0);
  readonly StatusPedido = StatusPedido;
  readonly MeioPagamento = MeioPagamento;

  // Menu de contexto
  readonly menuContexto = signal<{ pedidoId: string; x: number; y: number } | null>(null);

  // Formulários
  clienteForm: FormGroup;
  pedidoForm: FormGroup;

  constructor() {
    this.clienteForm = this.fb.group({
      nome: ['', [Validators.required]],
      telefone: ['', [Validators.required]],
      email: [''],
      cpf: [''],
      observacoes: ['']
    });

    this.pedidoForm = this.fb.group({
      clienteId: ['', [Validators.required]],
      observacoes: ['']
    });
  }

  ngOnInit(): void {
    if (this.isBrowser) {
      this.carregarDados();
      // Fecha menu de contexto ao clicar fora
      document.addEventListener('click', () => this.fecharMenuContexto());
      document.addEventListener('contextmenu', (e) => {
        // Só fecha se não for no card de pedido
        const target = e.target as HTMLElement;
        if (!target.closest('.pedido-card')) {
          this.fecharMenuContexto();
        }
      });
      
      // Atualiza o valor do meio de pagamento quando o valor restante ou meio selecionado mudar
      effect(() => {
        const restante = this.valorRestante();
        const meioSelecionado = this.meioPagamentoSelecionado();
        const valorAtual = this.valorMeioPagamento();
        
        if (meioSelecionado && restante > 0) {
          // Se o valor atual é 0 ou maior que o restante, atualiza para o restante
          if (valorAtual === 0 || valorAtual > restante) {
            setTimeout(() => {
              this.valorMeioPagamento.set(restante);
            }, 0);
          }
        } else if (restante <= 0) {
          setTimeout(() => {
            this.valorMeioPagamento.set(0);
          }, 0);
        }
      }, { allowSignalWrites: true });
    }
  }

  private carregarDados(): void {
    this.pedidosComposable.carregarPedidos();
    this.pedidosComposable.carregarProdutos();
    this.carregarClientes();
  }

  private carregarClientes(telefone?: string, nome?: string): void {
    this.clienteService.listar({ telefone, nome })
      .subscribe({
        next: (clientes) => this.clientes.set(clientes),
        error: (error) => console.error('Erro ao carregar clientes:', error)
      });
  }

  buscarClientes(texto: string): void {
    if (texto && texto.trim().length >= 2) {
      const textoLimpo = texto.trim();
      // Tenta buscar por telefone primeiro, depois por nome
      if (/^\d/.test(textoLimpo)) {
        // Se começa com número, busca por telefone
        this.carregarClientes(textoLimpo, undefined);
      } else {
        // Se começa com letra, busca por nome
        this.carregarClientes(undefined, textoLimpo);
      }
    } else {
      this.carregarClientes();
    }
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
    this.clienteSelecionado.set(null);
    this.itensSelecionados.set([]);
    this.meiosPagamento.set([]);
    this.meioPagamentoSelecionado.set(null);
    this.valorMeioPagamento.set(0);
    this.pesquisaProduto.set('');
    this.categoriaFiltro.set(null);
    this.clienteForm.reset();
    this.pedidoForm.reset();
  }

  fecharFormulario(): void {
    this.mostrarFormulario.set(false);
    this.clienteSelecionado.set(null);
    this.itensSelecionados.set([]);
    this.meiosPagamento.set([]);
    this.meioPagamentoSelecionado.set(null);
    this.valorMeioPagamento.set(0);
    this.clienteForm.reset();
    this.pedidoForm.reset();
  }
  
  onMeioPagamentoSelecionado(): void {
    const valorRestante = this.valorRestante();
    if (valorRestante > 0) {
      this.valorMeioPagamento.set(valorRestante);
    } else {
      this.valorMeioPagamento.set(0);
    }
  }
  
  adicionarMeioPagamento(): void {
    const meioPagamento = this.meioPagamentoSelecionado();
    const valor = this.valorMeioPagamento();
    
    if (!meioPagamento) {
      return;
    }
    
    if (valor <= 0) {
      if (this.isBrowser) {
        alert('O valor deve ser maior que zero.');
      }
      return;
    }
    
    const total = this.calcularTotal();
    const totalJaAdicionado = this.calcularTotalMeiosPagamento();
    const valorRestante = total - totalJaAdicionado;
    
    if (valor > valorRestante) {
      if (this.isBrowser) {
        alert(`O valor não pode ser maior que o restante (${this.formatarPreco(valorRestante)}).`);
      }
      return;
    }
    
    const novoMeioPagamento: MeioPagamentoPedido = {
      meioPagamento: meioPagamento,
      valor: valor
    };
    
    this.meiosPagamento.update(lista => [...lista, novoMeioPagamento]);
    this.meioPagamentoSelecionado.set(null);
    this.valorMeioPagamento.set(0);
    // O effect vai atualizar automaticamente quando o valor restante mudar
  }
  
  removerMeioPagamento(index: number): void {
    this.meiosPagamento.update(lista => lista.filter((_, i) => i !== index));
  }
  
  calcularTotalMeiosPagamento(): number {
    return this.meiosPagamento().reduce((total, mp) => total + mp.valor, 0);
  }
  
  valorRestante(): number {
    return this.calcularTotal() - this.calcularTotalMeiosPagamento();
  }

  parseValorMeioPagamento(value: string): number {
    const parsed = parseFloat(value);
    return isNaN(parsed) ? 0 : parsed;
  }

  selecionarCliente(cliente: Cliente): void {
    this.clienteSelecionado.set(cliente);
    this.pedidoForm.patchValue({ clienteId: cliente.id });
  }

  criarCliente(): void {
    if (this.clienteForm.invalid) {
      return;
    }

    const request: CriarClienteRequest = this.clienteForm.value;
    this.clienteService.criar(request)
      .subscribe({
        next: (cliente) => {
          this.clientes.update(lista => [...lista, cliente]);
          this.selecionarCliente(cliente);
        },
        error: (error) => {
          console.error('Erro ao criar cliente:', error);
          if (this.isBrowser) {
            alert('Erro ao criar cliente. Tente novamente.');
          }
        }
      });
  }

  adicionarItem(produto: Produto): void {
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
      // Adiciona um ID único baseado no timestamp para tracking
      (novoItem as any).itemId = `${produto.id}-${Date.now()}-${Math.random()}`;
      this.itensSelecionados.update(lista => [...lista, novoItem]);
    }
  }

  removerItem(index: number): void {
    this.itensSelecionados.update(lista => lista.filter((_, i) => i !== index));
  }

  atualizarQuantidade(index: number, quantidade: number): void {
    if (quantidade <= 0) {
      this.removerItem(index);
      return;
    }
    const itens = this.itensSelecionados();
    itens[index].quantidade = quantidade;
    this.itensSelecionados.set([...itens]);
  }

  criarPedido(): void {
    if (this.pedidoForm.invalid || !this.clienteSelecionado() || this.itensSelecionados().length === 0) {
      if (this.isBrowser) {
        alert('Preencha todos os campos obrigatórios e adicione pelo menos um item.');
      }
      return;
    }

    // Prepara request removendo campos vazios/undefined
    const request: any = {
      clienteId: this.clienteSelecionado()!.id,
      clienteNome: this.clienteSelecionado()!.nome,
      itens: this.itensSelecionados().map(item => {
        const itemRequest: any = {
          produtoId: item.produtoId,
          quantidade: item.quantidade
        };
        if (item.observacoes && item.observacoes.trim()) {
          itemRequest.observacoes = item.observacoes.trim();
        }
        return itemRequest;
      })
    };
    
    if (this.pedidoForm.value.observacoes && this.pedidoForm.value.observacoes.trim()) {
      request.observacoes = this.pedidoForm.value.observacoes.trim();
    }
    
    request.meiosPagamento = this.meiosPagamento();

    this.pedidoService.criar(request)
      .subscribe({
        next: (pedidoCriado) => {
          // Atualiza o signal imediatamente para atualizar contadores em tempo real
          this.pedidosComposable.atualizarPedidoNoSignal(pedidoCriado);
          // Recarrega todos os pedidos de forma assíncrona para garantir sincronização
          setTimeout(() => {
            this.pedidosComposable.carregarPedidos();
          }, 0);
          this.fecharFormulario();
        },
        error: (error) => {
          console.error('Erro ao criar pedido:', error);
          let mensagem = 'Erro ao criar pedido. Verifique se todos os campos estão preenchidos corretamente.';
          
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

  calcularTotal(): number {
    return this.itensSelecionados().reduce((total, item) => {
      const produto = this.buscarProdutoPorId(item.produtoId);
      if (produto) {
        return total + (produto.preco * item.quantidade);
      }
      return total;
    }, 0);
  }

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

  obterItemId(item: ItemPedidoRequest): string {
    return (item as any).itemId || item.produtoId;
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

  obterStatusDisponiveis(statusAtual: StatusPedido): StatusPedido[] {
    // Status disponíveis para mudança (exceto CANCELADO que não pode ser alterado)
    const todosStatus = [
      StatusPedido.PREPARANDO,
      StatusPedido.PRONTO,
      StatusPedido.FINALIZADO
    ];
    
    // Retorna todos os status exceto o atual
    return todosStatus.filter(s => s !== statusAtual);
  }

  obterNomeStatus(status: StatusPedido): string {
    const nomes: Record<StatusPedido, string> = {
      [StatusPedido.PREPARANDO]: 'Preparando',
      [StatusPedido.PRONTO]: 'Pronto',
      [StatusPedido.FINALIZADO]: 'Finalizado',
      [StatusPedido.PENDENTE]: 'Pendente',
      [StatusPedido.CANCELADO]: 'Cancelado'
    };
    return nomes[status] || status;
  }

  mudarStatusViaMenu(pedidoId: string, novoStatus: StatusPedido): void {
    this.fecharMenuContexto();
    this.atualizarStatus(pedidoId, novoStatus);
  }

  obterPedidoDoMenu(): Pedido | null {
    const menu = this.menuContexto();
    if (!menu) return null;
    return this.pedidosFiltrados().find(p => p.id === menu.pedidoId) || null;
  }

  produtosFiltrados(): Produto[] {
    let produtos = this.produtos();
    
    // Filtro por categoria
    const categoria = this.categoriaFiltro();
    if (categoria) {
      produtos = produtos.filter(p => p.categoria === categoria);
    }
    
    // Filtro por texto
    const texto = this.pesquisaProduto().toLowerCase().trim();
    if (texto) {
      produtos = produtos.filter(p => 
        p.nome.toLowerCase().includes(texto) ||
        p.descricao?.toLowerCase().includes(texto) ||
        p.categoria.toLowerCase().includes(texto)
      );
    }
    
    return produtos;
  }
  
  categoriasUnicas(): string[] {
    const categorias = new Set<string>();
    this.produtos().forEach(p => {
      if (p.categoria) {
        categorias.add(p.categoria);
      }
    });
    return Array.from(categorias).sort();
  }
  
  filtrarPorCategoria(categoria: string | null): void {
    this.categoriaFiltro.set(categoria);
  }

  pesquisarProduto(texto: string): void {
    this.pesquisaProduto.set(texto);
  }

  formatarData(data: string): string {
    return new Date(data).toLocaleString('pt-BR');
  }

  formatarPreco(preco: number): string {
    return `R$ ${preco.toFixed(2).replace('.', ',')}`;
  }
}

