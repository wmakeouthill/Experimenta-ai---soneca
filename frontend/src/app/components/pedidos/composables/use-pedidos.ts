import { signal, computed, inject, effect, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, finalize, switchMap, tap, retry, filter, takeWhile } from 'rxjs/operators';
import { of, timer, Subject, Subscription } from 'rxjs';
import { PedidoService, Pedido, StatusPedido } from '../../../services/pedido.service';
import { ProdutoService, Produto } from '../../../services/produto.service';

type EstadoCarregamento = 'idle' | 'carregando' | 'sucesso' | 'erro';

export function usePedidos() {
  const pedidoService = inject(PedidoService);
  const produtoService = inject(ProdutoService);
  const destroyRef = inject(DestroyRef);

  // Estados
  const pedidos = signal<Pedido[]>([]);
  const produtos = signal<Produto[]>([]);
  const estado = signal<EstadoCarregamento>('idle');
  const erro = signal<string | null>(null);
  const pollingAtivo = signal<boolean>(false);

  // Filtros - sempre inicia com PENDENTE (aguardando) selecionado
  const statusSelecionado = signal<StatusPedido>(StatusPedido.PENDENTE);
  const pesquisaTexto = signal<string>('');

  // Controle de pedidos já processados (para evitar duplicidade de notificação)
  // Armazena IDs dos pedidos já vistos nesta sessão
  const pedidosConhecidos = new Set<string>();

  // Evento para notificar novos pedidos
  const onNovoPedido = new Subject<Pedido>();

  // Computed
  const pedidosFiltrados = computed(() => {
    let resultado = [...pedidos()];

    // Filtro por status (sempre filtra, pois sempre tem um status selecionado)
    resultado = resultado.filter(p => p.status === statusSelecionado());

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
      aguardando: todos.filter(p => p.status === StatusPedido.PENDENTE),
      preparando: todos.filter(p => p.status === StatusPedido.PREPARANDO),
      pronto: todos.filter(p => p.status === StatusPedido.PRONTO),
      finalizado: todos.filter(p => p.status === StatusPedido.FINALIZADO),
      cancelado: todos.filter(p => p.status === StatusPedido.CANCELADO)
    };
  });

  const temPedidos = computed(() => pedidos().length > 0);
  const estaCarregando = computed(() => estado() === 'carregando');

  // Métodos
  const carregarPedidos = (filters?: {
    status?: StatusPedido;
    dataInicio?: string;
    dataFim?: string;
    sessaoId?: string;
  }) => {
    // Se for polling, não muda estado para 'carregando' para não piscar a tela
    if (!pollingAtivo()) {
      estado.set('carregando');
    }
    erro.set(null);

    // Carrega pedidos com filtros (incluindo sessão se fornecida)
    // O filtro por status é aplicado pelo computed pedidosFiltrados
    pedidoService.listar(filters)
      .pipe(
        catchError((error) => {
          const mensagem = error.error?.message || error.message || 'Erro ao carregar pedidos';
          erro.set(mensagem);
          if (!pollingAtivo()) {
            estado.set('erro');
          }
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
        processarNovosPedidos(resultado);
        pedidos.set(resultado);
      });
  };

  const processarNovosPedidos = (novosPedidos: Pedido[]) => {
    // Se é a primeira carga (pedidosConhecidos vazio), apenas popula o Set
    // para não notificar tudo como "novo" ao abrir a tela
    if (pedidosConhecidos.size === 0) {
      novosPedidos.forEach(p => pedidosConhecidos.add(p.id));
      return;
    }

    // Verifica se há pedidos novos
    novosPedidos.forEach(pedido => {
      if (!pedidosConhecidos.has(pedido.id)) {
        pedidosConhecidos.add(pedido.id);

        // Só notifica se for um pedido RECENTE (criado nos últimos 5 minutos)
        // Isso evita notificar pedidos antigos que por algum motivo apareceram agora
        const dataPedido = new Date(pedido.createdAt || pedido.dataPedido);
        const cincoMinutosAtras = new Date(Date.now() - 5 * 60 * 1000);

        if (dataPedido > cincoMinutosAtras) {
          console.log('Novo pedido detectado:', pedido.numeroPedido);
          onNovoPedido.next(pedido);
        }
      }
    });
  };

  const carregarProdutos = () => {
    produtoService.listar()
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

  const filtrarPorStatus = (status: StatusPedido) => {
    statusSelecionado.set(status);
    // Não precisa recarregar, o computed pedidosFiltrados já filtra automaticamente
  };

  const pesquisar = (texto: string) => {
    pesquisaTexto.set(texto);
  };

  const limparFiltros = () => {
    // Não limpa o status, apenas a pesquisa (sempre mantém um status selecionado)
    pesquisaTexto.set('');
  };

  const atualizarPedidoNoSignal = (pedidoAtualizado: Pedido) => {
    // Sempre cria uma nova referência para garantir detecção de mudança
    pedidos.update(lista => {
      const index = lista.findIndex(p => p.id === pedidoAtualizado.id);
      if (index >= 0) {
        // Atualiza o pedido existente - cria nova array e novo objeto
        const novaLista = [...lista];
        novaLista[index] = { ...pedidoAtualizado }; // Garante novo objeto
        return novaLista;
      } else {
        // Se não encontrou, adiciona (novo pedido ou mudou de status)
        // Adiciona aos conhecidos para não disparar notificação duplicada
        pedidosConhecidos.add(pedidoAtualizado.id);
        return [...lista, { ...pedidoAtualizado }]; // Garante novo objeto
      }
    });
  };

  const carregarTodosPedidos = (sessaoId?: string) => {
    estado.set('carregando');
    erro.set(null);

    const filters = sessaoId ? { sessaoId } : undefined;
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
        processarNovosPedidos(resultado);
        pedidos.set(resultado);
      });
  };

  // Lógica de Polling
  let pollingSubscription: Subscription | null = null;

  const iniciarPolling = (sessaoId?: string) => {
    if (pollingAtivo()) return;

    pollingAtivo.set(true);
    console.log('Iniciando polling de pedidos...');

    // Polling a cada 5 segundos
    pollingSubscription = timer(0, 5000).pipe(
      takeWhile(() => pollingAtivo()),
      switchMap(() => {
        const filters = sessaoId ? { sessaoId } : undefined;
        return pedidoService.listar(filters).pipe(
          catchError(err => {
            console.error('Erro no polling:', err);
            return of([]); // Continua o polling mesmo com erro
          })
        );
      }),
      takeUntilDestroyed(destroyRef) // Garante limpeza ao destruir componente
    ).subscribe(resultado => {
      if (resultado.length > 0) {
        processarNovosPedidos(resultado);
        pedidos.set(resultado);

        // Se estava com erro ou carregando, atualiza para sucesso
        if (estado() !== 'sucesso') {
          estado.set('sucesso');
        }
      }
    });
  };

  const pararPolling = () => {
    pollingAtivo.set(false);
    if (pollingSubscription) {
      pollingSubscription.unsubscribe();
      pollingSubscription = null;
    }
    console.log('Polling de pedidos parado.');
  };

  return {
    // Estados
    pedidos,
    produtos,
    estado,
    erro,
    statusSelecionado,
    pesquisaTexto,
    pollingAtivo,

    // Observables
    onNovoPedido: onNovoPedido.asObservable(),

    // Computed
    pedidosFiltrados,
    pedidosPorStatus,
    temPedidos,
    estaCarregando,

    // Métodos
    carregarPedidos,
    carregarTodosPedidos,
    carregarProdutos,
    filtrarPorStatus,
    pesquisar,
    limparFiltros,
    atualizarPedidoNoSignal,
    iniciarPolling,
    pararPolling
  };
}

