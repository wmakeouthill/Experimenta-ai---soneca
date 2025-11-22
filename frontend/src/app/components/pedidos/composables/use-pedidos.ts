import { signal, computed, inject } from '@angular/core';
import { catchError, finalize } from 'rxjs/operators';
import { of } from 'rxjs';
import { PedidoService, Pedido, StatusPedido } from '../../../services/pedido.service';
import { ProdutoService, Produto } from '../../../services/produto.service';

type EstadoCarregamento = 'idle' | 'carregando' | 'sucesso' | 'erro';

export function usePedidos() {
  const pedidoService = inject(PedidoService);
  const produtoService = inject(ProdutoService);

  // Estados
  const pedidos = signal<Pedido[]>([]);
  const produtos = signal<Produto[]>([]);
  const estado = signal<EstadoCarregamento>('idle');
  const erro = signal<string | null>(null);

  // Filtros
  const statusSelecionado = signal<StatusPedido | null>(null);
  const pesquisaTexto = signal<string>('');

  // Computed
  const pedidosFiltrados = computed(() => {
    let resultado = [...pedidos()];

    // Filtro por status
    if (statusSelecionado()) {
      resultado = resultado.filter(p => p.status === statusSelecionado());
    }

    // Filtro por texto (busca em número do pedido, nome do cliente)
    if (pesquisaTexto().trim()) {
      const texto = pesquisaTexto().toLowerCase();
      resultado = resultado.filter(p => 
        p.numeroPedido.toLowerCase().includes(texto) ||
        p.clienteNome.toLowerCase().includes(texto)
      );
    }

    return resultado;
  });

  const pedidosPorStatus = computed(() => {
    const todos = pedidos();
    return {
      preparando: todos.filter(p => p.status === StatusPedido.PREPARANDO),
      pronto: todos.filter(p => p.status === StatusPedido.PRONTO),
      finalizado: todos.filter(p => p.status === StatusPedido.FINALIZADO)
    };
  });

  const temPedidos = computed(() => pedidos().length > 0);
  const estaCarregando = computed(() => estado() === 'carregando');

  // Métodos
  const carregarPedidos = (filters?: {
    status?: StatusPedido;
    dataInicio?: string;
    dataFim?: string;
  }) => {
    estado.set('carregando');
    erro.set(null);

    pedidoService.listar(filters)
      .pipe(
        catchError((error) => {
          const mensagem = error.error?.message || error.message || 'Erro ao carregar pedidos';
          erro.set(mensagem);
          estado.set('erro');
          console.error('Erro ao carregar pedidos:', error);
          return of([]);
        }),
        finalize(() => {
          if (estado() === 'carregando') {
            estado.set('sucesso');
          }
        })
      )
      .subscribe((resultado) => {
        pedidos.set(resultado);
      });
  };

  const carregarProdutos = () => {
    produtoService.listar({ disponivel: true })
      .pipe(
        catchError((error) => {
          console.error('Erro ao carregar produtos:', error);
          return of([]);
        })
      )
      .subscribe((resultado) => {
        produtos.set(resultado);
      });
  };

  const filtrarPorStatus = (status: StatusPedido | null) => {
    statusSelecionado.set(status);
  };

  const pesquisar = (texto: string) => {
    pesquisaTexto.set(texto);
  };

  const limparFiltros = () => {
    statusSelecionado.set(null);
    pesquisaTexto.set('');
  };

  return {
    // Estados
    pedidos,
    produtos,
    estado,
    erro,
    statusSelecionado,
    pesquisaTexto,

    // Computed
    pedidosFiltrados,
    pedidosPorStatus,
    temPedidos,
    estaCarregando,

    // Métodos
    carregarPedidos,
    carregarProdutos,
    filtrarPorStatus,
    pesquisar,
    limparFiltros
  };
}

