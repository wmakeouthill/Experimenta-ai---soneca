import { isPlatformBrowser } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, PLATFORM_ID } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { of } from 'rxjs';
import { catchError, filter } from 'rxjs/operators';
import { ToastComponent } from './components/shared/toast/toast.component';
import { ConexaoStatus } from './interceptors/retry-get.interceptor';
import { AuthService } from './services/auth.service';
import { ImpressaoService } from './services/impressao.service';
import { NotificationService } from './services/notification.service';
import { PedidoPollingService } from './services/pedido-polling.service';
import { SessaoTrabalhoService } from './services/sessao-trabalho.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ToastComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent implements OnInit {
  title = 'Snackbar System';
  readonly reconectando = inject(ConexaoStatus).reconectando;

  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);
  private readonly router = inject(Router);
  private readonly pollingService = inject(PedidoPollingService);
  private readonly sessaoService = inject(SessaoTrabalhoService);
  private readonly authService = inject(AuthService);
  private readonly impressaoService = inject(ImpressaoService);
  private readonly notificationService = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  // Rotas que não iniciam polling/impressão automática: as públicas e o totem (o totem imprime
  // só o próprio pedido; com o polling global reimprimiria todos os pedidos da loja)
  private readonly rotasSemServicosGlobais = ['/mesa/', '/pedido-mesa/', '/autoatendimento'];

  // Rotas onde a notificação de novo pedido deve ser suprimida
  // (ex: auto-atendimento cria o pedido na própria tela, não precisa notificar)
  private readonly rotasSemNotificacao = ['/autoatendimento', '/mesa/', '/pedido-mesa/'];

  ngOnInit(): void {
    if (this.isBrowser) {
      // Usa window.location pois this.router.url pode não estar atualizado no ngOnInit
      const urlAtual = window.location.pathname;

      // SEMPRE configura a impressão automática (independente de sessão ativa).
      // A subscription fica ativa e imprime quando o polling detectar novos pedidos.
      // Isso corrige o bug onde o operador loga depois do app iniciar e a
      // impressão automática nunca era ativada.
      if (!this.isRotaSemServicosGlobais(urlAtual)) {
        this.configurarImpressaoAutomatica();
        this.iniciarServicosGlobais();
      }

      // Monitora mudanças de rota para iniciar/parar serviços
      this.router.events
        .pipe(
          filter((event): event is NavigationEnd => event instanceof NavigationEnd),
          takeUntilDestroyed(this.destroyRef)
        )
        .subscribe(event => {
          // Deslogado (logout ou 401) também para: senão o polling segue batendo 401 no login, cada 401
          // desloga de novo e, no relogin, pollingAtivo() impede reiniciar com a sessão atual
          if (this.isRotaSemServicosGlobais(event.urlAfterRedirects) || !this.authService.estaAutenticado()) {
            // Rota pública, totem ou sem login - para os serviços
            this.pollingService.pararPolling();
          } else if (!this.pollingService.pollingAtivo()) {
            // Voltou para rota autenticada e polling não está ativo - reinicia
            this.iniciarServicosGlobais();
          }
        });
    }
  }

  private isRotaSemServicosGlobais(url: string): boolean {
    return this.rotasSemServicosGlobais.some(rota => url.includes(rota));
  }

  private iniciarServicosGlobais() {
    // Polling e impressão são do balcão: o TOTEM não lê sessões (403) e, fora de /autoatendimento,
    // o 403 derrubava o login do quiosque
    if (!this.authService.isAdministrador() && !this.authService.isOperador()) {
      return;
    }
    // Verifica se há sessão ativa para iniciar o polling
    this.sessaoService.buscarAtiva().subscribe({
      next: sessao => {
        if (sessao) {
          console.log('Sessão ativa encontrada. Iniciando serviços globais...');
          this.pollingService.iniciarPolling(sessao.id);
        }
      },
      error: () => {
        console.log('Nenhuma sessão ativa encontrada ou erro ao buscar.');
      },
    });
  }

  private configurarImpressaoAutomatica() {
    this.pollingService.onNovoPedido.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(pedido => {
      console.log(
        '🖨️ Detectado novo pedido no AppComponent. Iniciando impressão...',
        pedido.numeroExibicao || pedido.numeroPedido
      );

      // Notificação Global - suprimida em certas rotas (autoatendimento, mesa)
      const rotaAtual = this.router.url;
      const numeroChamada = pedido.numeroExibicao || pedido.numeroPedido;
      if (!this.rotasSemNotificacao.some(r => rotaAtual.includes(r))) {
        this.notificationService.sucesso(`🔔 Novo pedido recebido: ${numeroChamada}`);
      }

      this.imprimirCupomAutomatico(pedido.id);
    });
  }

  private imprimirCupomAutomatico(pedidoId: string): void {
    this.impressaoService
      .buscarConfiguracao()
      .pipe(
        catchError(() => {
          console.warn(
            'Configuração de impressora não encontrada. Impressão automática cancelada.'
          );
          return of(null);
        })
      )
      .subscribe(config => {
        if (!config || !config.ativa) {
          console.warn('Impressora não configurada ou inativa. Impressão automática cancelada.');
          return;
        }

        this.impressaoService
          .imprimirCupom({
            pedidoId,
            tipoImpressora: config.tipoImpressora,
            nomeEstabelecimento: config.nomeEstabelecimento,
            enderecoEstabelecimento: config.enderecoEstabelecimento,
            telefoneEstabelecimento: config.telefoneEstabelecimento,
            cnpjEstabelecimento: config.cnpjEstabelecimento,
          })
          .pipe(
            catchError(error => {
              console.error('Erro ao imprimir cupom automaticamente:', error);
              return of(null);
            })
          )
          .subscribe(response => {
            if (response?.sucesso) {
              console.log('✅ Cupom impresso com sucesso via AppComponent!');
              // Pode adicionar um som ou notificação global aqui se desejar
            }
          });
      });
  }
}
