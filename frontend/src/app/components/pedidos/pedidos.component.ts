import { Component, inject, PLATFORM_ID, OnInit, signal, computed } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { usePedidos } from './composables/use-pedidos';
import { PedidoService, StatusPedido, CriarPedidoRequest, ItemPedidoRequest } from '../../services/pedido.service';
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
  styleUrl: './pedidos.component.css'
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

  // Modal/Formulário states
  readonly mostrarFormulario = signal(false);
  readonly clientes = signal<Cliente[]>([]);
  readonly clienteSelecionado = signal<Cliente | null>(null);
  readonly itensSelecionados = signal<ItemPedidoRequest[]>([]);
  readonly StatusPedido = StatusPedido;

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
    this.clienteForm.reset();
    this.pedidoForm.reset();
  }

  fecharFormulario(): void {
    this.mostrarFormulario.set(false);
    this.clienteSelecionado.set(null);
    this.itensSelecionados.set([]);
    this.clienteForm.reset();
    this.pedidoForm.reset();
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

    this.pedidoService.criar(request)
      .subscribe({
        next: () => {
          this.carregarDados();
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
        next: () => {
          this.carregarDados();
        },
        error: (error) => {
          console.error('Erro ao atualizar status:', error);
          if (this.isBrowser) {
            alert('Erro ao atualizar status. Tente novamente.');
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
        next: () => {
          this.carregarDados();
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

  produtosFiltrados(): Produto[] {
    const texto = this.pesquisaProduto().toLowerCase().trim();
    if (!texto) {
      return this.produtos();
    }
    return this.produtos().filter(p => 
      p.nome.toLowerCase().includes(texto) ||
      p.descricao?.toLowerCase().includes(texto) ||
      p.categoria.toLowerCase().includes(texto)
    );
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

