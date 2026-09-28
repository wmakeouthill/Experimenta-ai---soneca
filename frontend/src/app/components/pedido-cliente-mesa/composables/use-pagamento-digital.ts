import { computed, signal } from '@angular/core';
import { PagamentoService, PixCobrancaCriadaDTO, StatusPagamento } from '../../../services/pagamento.service';

export type EtapaPix = 'oculto' | 'aguardando' | 'aprovado' | 'expirado' | 'erro';

/**
 * Controla o fluxo de PIX pre-pago do pedido-mesa: cria a cobranca, exibe o QR,
 * faz a contagem regressiva e o polling do status ate a aprovacao.
 */
export function usePagamentoDigital(pagamentoService: PagamentoService) {
  const etapa = signal<EtapaPix>('oculto');
  const cobranca = signal<PixCobrancaCriadaDTO | null>(null);
  const correlationId = signal<string | null>(null);
  const pedidoId = signal<string | null>(null);
  const tempoRestante = signal(0);
  const erro = signal<string | null>(null);

  const expirado = computed(() => etapa() === 'expirado');
  const aprovado = computed(() => etapa() === 'aprovado');

  let timer: ReturnType<typeof setInterval> | undefined;
  let poll: ReturnType<typeof setInterval> | undefined;

  function pararTimers(): void {
    if (timer) { clearInterval(timer); timer = undefined; }
    if (poll) { clearInterval(poll); poll = undefined; }
  }

  function iniciarContagem(expiracaoEm: string): void {
    const expiraMs = new Date(expiracaoEm).getTime();
    const atualizar = () => {
      const restante = Math.max(0, Math.floor((expiraMs - Date.now()) / 1000));
      tempoRestante.set(restante);
      if (restante <= 0) {
        etapa.set('expirado');
        pararTimers();
      }
    };
    atualizar();
    timer = setInterval(atualizar, 1000);
  }

  function iniciarPolling(): void {
    poll = setInterval(() => {
      const id = correlationId();
      if (!id) return;
      pagamentoService.buscarStatus(id).subscribe({
        next: (pagamento) => {
          const status: StatusPagamento = pagamento.status;
          if (status === 'APROVADO') {
            etapa.set('aprovado');
            pararTimers();
          } else if (
            status === 'EXPIRADO' ||
            status === 'CANCELADO' ||
            status === 'FALHA_TECNICA' ||
            status === 'NEGADO'
          ) {
            etapa.set('expirado');
            pararTimers();
          }
        },
        error: () => { /* mantem tentando ate expirar */ },
      });
    }, 3000);
  }

  /**
   * Cria o pedido pre-pago com PIX e inicia contagem + polling.
   * `request` e o corpo do CriarPedidoMesaRequest (mesaToken, clienteId, itens...).
   */
  function iniciar(request: unknown): void {
    const novoCorrelationId = crypto.randomUUID();
    correlationId.set(novoCorrelationId);
    etapa.set('aguardando');
    erro.set(null);
    pagamentoService.criarPedidoMesaComPix(request, novoCorrelationId).subscribe({
      next: (resposta) => {
        cobranca.set(resposta.pagamento);
        pedidoId.set(resposta.pedido.id);
        iniciarContagem(resposta.pagamento.expiracaoEm);
        iniciarPolling();
      },
      error: (err: { status?: number; error?: { message?: string } }) => {
        etapa.set('erro');
        // 422 traz o motivo de negócio (ex.: loja pausada) — mais útil que a mensagem genérica
        const motivo = err.status === 422 ? err.error?.message : undefined;
        erro.set(motivo || 'Nao foi possivel gerar a cobranca PIX. Tente novamente.');
      },
    });
  }

  function regenerar(request: unknown): void {
    pararTimers();
    iniciar(request);
  }

  function encerrar(): void {
    pararTimers();
    etapa.set('oculto');
    cobranca.set(null);
    correlationId.set(null);
    pedidoId.set(null);
    tempoRestante.set(0);
    erro.set(null);
  }

  function formatarTempo(segundos: number): string {
    const min = Math.floor(segundos / 60);
    const seg = segundos % 60;
    return `${min}:${seg.toString().padStart(2, '0')}`;
  }

  return {
    etapa: etapa.asReadonly(),
    cobranca: cobranca.asReadonly(),
    pedidoId: pedidoId.asReadonly(),
    tempoRestante: tempoRestante.asReadonly(),
    erro: erro.asReadonly(),
    expirado,
    aprovado,
    iniciar,
    regenerar,
    encerrar,
    formatarTempo,
  };
}
