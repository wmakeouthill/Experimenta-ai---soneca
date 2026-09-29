import { Injectable, inject, signal, NgZone } from '@angular/core';
import { catchError, switchMap, takeWhile } from 'rxjs/operators';
import { of, timer, Subject, Subscription, Observable } from 'rxjs';
import { PedidoService, Pedido } from './pedido.service';

@Injectable({
  providedIn: 'root'
})
export class PedidoPollingService {
  private readonly pedidoService = inject(PedidoService);
  private readonly ngZone = inject(NgZone);

  // Estados Globais
  readonly pedidos = signal<Pedido[]>([]);
  readonly pollingAtivo = signal<boolean>(false);
  readonly erro = signal<string | null>(null);

  // Evento para notificar novos pedidos
  private readonly _onNovoPedido = new Subject<Pedido>();
  readonly onNovoPedido = this._onNovoPedido.asObservable();

  // Controle de pedidos já processados
  private readonly pedidosConhecidos = new Set<string>();
  // Flag e não o tamanho do Set: sessão aberta com zero pedidos deixava o Set vazio,
  // e o primeiro pedido do dia caía de novo em "primeira carga" (sem aviso nem impressão)
  private primeiraCargaFeita = false;
  private pollingSubscription: Subscription | null = null;
  // Sessão do polling ativo: recarregar() sem argumento usa ela, senão listaria pedidos de todas as sessões
  private sessaoIdAtual: string | undefined;

  iniciarPolling(sessaoId?: string) {
    if (this.pollingAtivo() && sessaoId === this.sessaoIdAtual) {
      return;
    }

    // Sessão mudou com polling ativo (ex.: caixa fechado e reaberto): reinicia com o novo filtro
    this.pararPolling();
    this.sessaoIdAtual = sessaoId;
    this.primeiraCargaFeita = false;
    // Sem sessão não há pedido em andamento (finalizar sessão exige fila vazia);
    // listar sem filtro baixaria o histórico inteiro, sem paginação, a cada 5 s
    if (!sessaoId) {
      this.pedidos.set([]);
      return;
    }
    this.pollingAtivo.set(true);
    console.log('Iniciando polling global de pedidos...');

    // Executa o timer fora da zona Angular para não bloquear hidratação/estabilidade
    // Isso evita o erro NG0506 (ApplicationRef.isStable() timeout)
    this.ngZone.runOutsideAngular(() => {
      // Polling a cada 5 segundos
      this.pollingSubscription = timer(0, 5000).pipe(
        takeWhile(() => this.pollingAtivo()),
        switchMap(() => {
          return this.pedidoService.listar({ sessaoId }).pipe(
            catchError(err => {
              console.error('Erro no polling global:', err);
              // Atualiza estado dentro da zona Angular
              this.ngZone.run(() => this.erro.set('Erro ao buscar pedidos'));
              return of([]); // Continua o polling mesmo com erro
            })
          );
        })
      ).subscribe(resultado => {
        // Executa atualizações de estado dentro da zona Angular para trigger change detection
        this.ngZone.run(() => {
          this.processarNovosPedidos(resultado);
          this.pedidos.set([...resultado]);
          this.erro.set(null);
        });
      });
    });
  }

  pararPolling() {
    this.pollingAtivo.set(false);
    if (this.pollingSubscription) {
      this.pollingSubscription.unsubscribe();
      this.pollingSubscription = null;
    }
  }

  private processarNovosPedidos(novosPedidos: Pedido[]) {
    // Primeira carga só registra o que já existe, para não reimprimir ao abrir a tela
    if (!this.primeiraCargaFeita) {
      this.primeiraCargaFeita = true;
      console.log('Primeira carga global de pedidos. Total:', novosPedidos.length);
      novosPedidos.forEach(p => this.pedidosConhecidos.add(p.id));
      return;
    }

    // Verifica se há pedidos novos
    novosPedidos.forEach(pedido => {
      if (!this.pedidosConhecidos.has(pedido.id)) {
        this.pedidosConhecidos.add(pedido.id);

        // Só notifica se for um pedido RECENTE (criado nos últimos 5 minutos)
        const dataPedido = new Date(pedido.createdAt || pedido.dataPedido);
        const cincoMinutosAtras = new Date(Date.now() - 5 * 60 * 1000);

        if (dataPedido > cincoMinutosAtras) {
          console.log('✅ Novo pedido detectado (Global):', pedido.numeroPedido);
          this._onNovoPedido.next(pedido);
        }
      }
    });
  }

  // Método para forçar recarga manual
  recarregar(sessaoId: string | undefined = this.sessaoIdAtual) {
    if (!sessaoId) {
      this.pedidos.set([]);
      return;
    }
    this.pedidoService.listar({ sessaoId }).subscribe({
      next: (resultado) => {
        this.processarNovosPedidos(resultado);
        // Força nova referência de array para garantir detecção de mudança
        this.pedidos.set([...resultado]);
      },
      error: (err) => console.error('Erro ao recarregar pedidos:', err)
    });
  }
}
