import { signal, computed, inject } from '@angular/core';
import { catchError, finalize } from 'rxjs/operators';
import { of } from 'rxjs';
import { PedidoService, Pedido, MeioPagamento } from '../../../services/pedido.service';
import { SessaoTrabalhoService, SessaoTrabalho } from '../../../services/sessao-trabalho.service';

type EstadoCarregamento = 'idle' | 'carregando' | 'sucesso' | 'erro';

export interface ResumoFaturamento {
  totalPedidos: number;
  totalFaturamento: number;
  porMeioPagamento: Array<{ meio: MeioPagamento; valor: number }>;
}

export function useHistoricoSessoes() {
  const pedidoService = inject(PedidoService);
  const sessaoService = inject(SessaoTrabalhoService);

  // Estados
  const sessoes = signal<SessaoTrabalho[]>([]);
  const sessaoSelecionada = signal<SessaoTrabalho | null>(null);
  const pedidos = signal<Pedido[]>([]);
  const estado = signal<EstadoCarregamento>('idle');
  const erro = signal<string | null>(null);

  // Computed
  const estaCarregando = computed(() => estado() === 'carregando');
  const temSessaoSelecionada = computed(() => sessaoSelecionada() !== null);
  const temPedidos = computed(() => pedidos().length > 0);

  const resumoFaturamento = computed((): ResumoFaturamento => {
    const pedidosAtuais = pedidos();
    
    if (pedidosAtuais.length === 0) {
      return {
        totalPedidos: 0,
        totalFaturamento: 0,
        porMeioPagamento: []
      };
    }

    const totalPedidos = pedidosAtuais.length;
    let totalFaturamento = 0;
    const porMeioPagamentoMap = new Map<MeioPagamento, number>();

    pedidosAtuais.forEach(pedido => {
      totalFaturamento += pedido.valorTotal;

      if (pedido.meiosPagamento && pedido.meiosPagamento.length > 0) {
        pedido.meiosPagamento.forEach(mp => {
          const valorAtual = porMeioPagamentoMap.get(mp.meioPagamento) || 0;
          porMeioPagamentoMap.set(mp.meioPagamento, valorAtual + mp.valor);
        });
      }
    });

    const porMeioPagamento = Array.from(porMeioPagamentoMap.entries()).map(([meio, valor]) => ({
      meio,
      valor
    }));

    return {
      totalPedidos,
      totalFaturamento,
      porMeioPagamento
    };
  });

  // Métodos
  const carregarSessoes = () => {
    estado.set('carregando');
    erro.set(null);

    sessaoService.listar()
      .pipe(
        catchError((error) => {
          const mensagem = error.error?.message || error.message || 'Erro ao carregar sessões';
          erro.set(mensagem);
          estado.set('erro');
          console.error('Erro ao carregar sessões:', error);
          return of([]);
        }),
        finalize(() => {
          if (estado() === 'carregando') {
            estado.set('sucesso');
          }
        })
      )
      .subscribe((resultado) => {
        sessoes.set(resultado);
      });
  };

  const selecionarSessao = (sessao: SessaoTrabalho | null) => {
    sessaoSelecionada.set(sessao);
    
    if (sessao) {
      carregarPedidosPorSessao(sessao.id);
    } else {
      pedidos.set([]);
    }
  };

  const carregarPedidosPorSessao = (sessaoId: string) => {
    estado.set('carregando');
    erro.set(null);

    pedidoService.listar({ sessaoId })
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

  const recarregar = () => {
    if (sessaoSelecionada()) {
      carregarPedidosPorSessao(sessaoSelecionada()!.id);
    } else {
      carregarSessoes();
    }
  };

  return {
    // Estados
    sessoes,
    sessaoSelecionada,
    pedidos,
    estado,
    erro,

    // Computed
    estaCarregando,
    temSessaoSelecionada,
    temPedidos,
    resumoFaturamento,

    // Métodos
    carregarSessoes,
    selecionarSessao,
    carregarPedidosPorSessao,
    recarregar
  };
}

