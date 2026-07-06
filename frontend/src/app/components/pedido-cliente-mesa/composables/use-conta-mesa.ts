import { signal } from '@angular/core';
import { ContaMesa, PagamentoService, PixCobrancaCriadaDTO } from '../../../services/pagamento.service';

export type EtapaConta = 'previa' | 'pagando' | 'aprovado' | 'expirado' | 'erro';

/**
 * Fluxo da conta pos-paga: consulta a previa dos pedidos em aberto e fecha a
 * conta gerando um unico PIX. O polling de status reutiliza buscarStatus.
 */
export function useContaMesa(pagamentoService: PagamentoService, mesaToken: string, clienteId: string) {
  const etapa = signal<EtapaConta>('previa');
  const conta = signal<ContaMesa | null>(null);
  const cobranca = signal<PixCobrancaCriadaDTO | null>(null);
  const correlationId = signal<string | null>(null);
  const erro = signal<string | null>(null);

  let poll: ReturnType<typeof setInterval> | undefined;

  function pararPoll(): void {
    if (poll) { clearInterval(poll); poll = undefined; }
  }

  function carregarPrevia(): void {
    pagamentoService.consultarConta(mesaToken, clienteId).subscribe({
      next: (c) => conta.set(c),
      error: () => erro.set('Nao foi possivel carregar a conta.'),
    });
  }

  function iniciarPolling(): void {
    poll = setInterval(() => {
      const id = correlationId();
      if (!id) return;
      pagamentoService.buscarStatus(id).subscribe({
        next: (pagamento) => {
          if (pagamento.status === 'APROVADO') {
            etapa.set('aprovado');
            pararPoll();
          } else if (['EXPIRADO', 'CANCELADO', 'FALHA_TECNICA', 'NEGADO'].includes(pagamento.status)) {
            etapa.set('expirado');
            pararPoll();
          }
        },
        error: () => { /* continua ate expirar */ },
      });
    }, 3000);
  }

  function fechar(): void {
    const novoCorrelationId = crypto.randomUUID();
    correlationId.set(novoCorrelationId);
    etapa.set('pagando');
    erro.set(null);
    pagamentoService.fecharConta(mesaToken, clienteId, novoCorrelationId).subscribe({
      next: (resposta) => {
        conta.set(resposta.conta);
        cobranca.set(resposta.pagamento);
        iniciarPolling();
      },
      error: () => {
        etapa.set('erro');
        erro.set('Nao foi possivel fechar a conta. Tente novamente.');
      },
    });
  }

  function encerrar(): void {
    pararPoll();
  }

  return {
    etapa: etapa.asReadonly(),
    conta: conta.asReadonly(),
    cobranca: cobranca.asReadonly(),
    erro: erro.asReadonly(),
    carregarPrevia,
    fechar,
    encerrar,
  };
}
