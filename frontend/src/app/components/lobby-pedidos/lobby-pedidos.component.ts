import { Component, OnInit, OnDestroy, inject, signal, computed, effect, ChangeDetectionStrategy, PLATFORM_ID, afterNextRender, Injector, NgZone } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { StatusPedido, Pedido } from '../../services/pedido.service';
import { useLobbyPedidos } from './composables/use-lobby-pedidos';
import { useAnimations } from './composables/use-animations';
import { OrderListComponent } from './components/order-list/order-list.component';
import { LobbyHeaderComponent } from './components/header/header.component';
import { LobbyTickerComponent } from './components/lobby-ticker/lobby-ticker.component';
import { LobbyInterludeComponent } from './components/lobby-interlude/lobby-interlude.component';
import { ConfigAnimacaoModalComponent, ConfigAnimacao } from './components/config-animacao-modal/config-animacao-modal.component';
import { ConfigAnimacaoService } from '../../services/config-animacao.service';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';
import { LobbyPiso, LobbyTema } from './models/lobby-ui.types';
import {
  carregarReelsLocalStorage,
  migrarReelsDeApi,
  montarTickerTexto,
  reelsParaApiUrls,
  salvarReelsLocalStorage,
} from './utils/lobby-promocoes.util';

@Component({
  selector: 'app-lobby-pedidos',
  standalone: true,
  imports: [
    CommonModule,
    LobbyHeaderComponent,
    LobbyInterludeComponent,
    OrderListComponent,
    LobbyTickerComponent,
    ConfigAnimacaoModalComponent,
  ],
  templateUrl: './lobby-pedidos.component.html',
  styleUrl: './lobby-pedidos.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LobbyPedidosComponent implements OnInit, OnDestroy {
  private readonly configAnimacaoService = inject(ConfigAnimacaoService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly injector = inject(Injector);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly ngZone = inject(NgZone);

  readonly pedidosAnteriores = signal<Pedido[]>([]);

  readonly lobbyPedidos = useLobbyPedidos();
  readonly animations = useAnimations();

  readonly mostrarInterludio = computed(() => this.animations.mostrarInterludio());
  readonly mostrarConfigModal = signal<boolean>(false);
  readonly tema = signal<LobbyTema>('escuro');
  readonly piso = signal<LobbyPiso>('terreo');
  /** Pedidos deste painel; pedido sem piso (legado) aparece nos dois. */
  readonly pedidosDoPiso = computed(() => {
    const piso = this.piso() === 'terreo' ? 'TERREO' : 'ANDAR';
    return this.lobbyPedidos.pedidos().filter(p => !p.piso || p.piso === piso);
  });
  readonly isAdministrador = this.authService.isAdministrador;
  readonly horaAtual = signal(this.formatarHora(new Date()));
  readonly relogioMs = signal(Date.now());

  // Expor StatusPedido para o template
  readonly StatusPedido = StatusPedido;

  private pollingInterval: ReturnType<typeof setInterval> | null = null;
  private readonly intervaloPolling = 3000; // 3 segundos
  private animacaoPeriodicaInterval: ReturnType<typeof setInterval> | null = null;
  private relogioInterval: ReturnType<typeof setInterval> | null = null;
  private readonly storageTemaKey = 'lobby-tema';
  private readonly storagePisoKey = 'lobby-piso';

  private get isBrowser(): boolean {
    return isPlatformBrowser(this.platformId);
  }

  constructor() {
    // Effect para detectar mudanças nos pedidos (no contexto de injeção)
    effect(() => {
      if (!isPlatformBrowser(this.platformId)) return;

      const pedidosAtuais = this.lobbyPedidos.pedidos();
      const pedidosAnt = this.pedidosAnteriores();

      if (pedidosAtuais.length !== pedidosAnt.length ||
        pedidosAtuais.some((p, i) => p.id !== pedidosAnt[i]?.id || p.status !== pedidosAnt[i]?.status)) {
        this.pedidosAnteriores.set([...pedidosAtuais]);
      }
    }, { allowSignalWrites: true });

    // Aguardar hidratação completar antes de inicializar dados
    afterNextRender(() => {
      if (!isPlatformBrowser(this.platformId)) return;

      // Inicializar dados
      this.lobbyPedidos.carregarSessaoAtiva();
      this.lobbyPedidos.carregarPedidos();
      this.iniciarPolling();
      this.carregarConfigAnimacao();
      this.iniciarAnimacaoPeriodica();
      this.iniciarRelogio();
      this.carregarPreferenciasUi();
    });
  }

  private carregarPreferenciasUi(): void {
    if (!this.isBrowser) return;

    const temaSalvo = localStorage.getItem(this.storageTemaKey);
    if (temaSalvo === 'claro' || temaSalvo === 'escuro') {
      this.tema.set(temaSalvo);
    }

    const pisoSalvo = localStorage.getItem(this.storagePisoKey);
    if (pisoSalvo === 'terreo' || pisoSalvo === 'andar') {
      this.piso.set(pisoSalvo);
    }
  }

  private persistirTema(valor: LobbyTema): void {
    if (this.isBrowser) {
      localStorage.setItem(this.storageTemaKey, valor);
    }
  }

  private persistirPiso(valor: LobbyPiso): void {
    if (this.isBrowser) {
      localStorage.setItem(this.storagePisoKey, valor);
    }
  }

  private iniciarRelogio(): void {
    this.ngZone.runOutsideAngular(() => {
      this.relogioInterval = setInterval(() => {
        this.ngZone.run(() => {
          const agora = new Date();
          this.horaAtual.set(this.formatarHora(agora));
          this.relogioMs.set(agora.getTime());
        });
      }, 1000);
    });
    this.horaAtual.set(this.formatarHora(new Date()));
    this.relogioMs.set(Date.now());
  }

  private formatarHora(data: Date): string {
    return `${this.pad2(data.getHours())}:${this.pad2(data.getMinutes())}`;
  }

  private pad2(valor: number): string {
    return valor < 10 ? `0${valor}` : `${valor}`;
  }

  ngOnInit() {
    // Tudo é feito no afterNextRender do constructor para evitar problemas de hidratação
  }

  private carregarConfigAnimacao() {
    this.configAnimacaoService.carregar().subscribe({
      next: (config) => {
        const reelsLocal = carregarReelsLocalStorage();
        const reels = migrarReelsDeApi(
          config.video1Url,
          config.video2Url,
          reelsLocal ?? config.reels
        );

        this.animations.animacaoConfig.set({
          animacaoAtivada: config.animacaoAtivada,
          intervaloAnimacao: config.intervaloAnimacao,
          duracaoAnimacao: config.duracaoAnimacao,
          reels,
          video1Url: config.video1Url || null,
          video2Url: config.video2Url || null
        });
        this.iniciarAnimacaoPeriodica();
      },
      // Sem a config do servidor, segue com a padrão
      error: () => this.iniciarAnimacaoPeriodica()
    });
  }

  ngOnDestroy() {
    if (this.pollingInterval) {
      clearInterval(this.pollingInterval);
    }
    if (this.animacaoPeriodicaInterval) {
      clearInterval(this.animacaoPeriodicaInterval);
    }
    if (this.relogioInterval) {
      clearInterval(this.relogioInterval);
    }
  }

  private iniciarPolling() {
    // Executa o polling fora da zona Angular para não bloquear hidratação/estabilidade
    this.ngZone.runOutsideAngular(() => {
      this.pollingInterval = setInterval(() => {
        // Executa a carga dentro da zona para trigger change detection
        this.ngZone.run(() => this.lobbyPedidos.carregarPedidos());
      }, this.intervaloPolling);
    });
  }

  // Métodos vazios para eventos do modo visualização (não fazem nada)
  handleMarcarComoPronto(_id: string) {
    // Modo visualização - não faz nada
  }

  handleRemover(_id: string) {
    // Modo visualização - não faz nada
  }

  handleTrocarModo() {
    // Modo visualização - não faz nada
  }

  handleAnimacaoManual() {
    if (this.mostrarInterludio()) return;
    this.animations.abrirInterludio();
  }

  handleFecharInterludio() {
    this.animations.fecharInterludio();
  }

  handleToggleTema() {
    const proximo: LobbyTema = this.tema() === 'escuro' ? 'claro' : 'escuro';
    this.tema.set(proximo);
    this.persistirTema(proximo);
  }

  handleTogglePiso() {
    const proximo: LobbyPiso = this.piso() === 'terreo' ? 'andar' : 'terreo';
    this.piso.set(proximo);
    this.persistirPiso(proximo);
  }

  handleAbrirConfig() {
    if (!this.isAdministrador()) {
      return;
    }
    this.mostrarConfigModal.set(true);
  }

  handleSalvarConfig(config: ConfigAnimacao) {
    const apiUrls = reelsParaApiUrls(config.reels);

    this.animations.animacaoConfig.set({
      animacaoAtivada: config.animacaoAtivada,
      intervaloAnimacao: config.intervaloAnimacao,
      duracaoAnimacao: config.duracaoAnimacao,
      reels: config.reels,
      video1Url: apiUrls.video1Url,
      video2Url: apiUrls.video2Url
    });

    if (this.isBrowser) {
      salvarReelsLocalStorage(config.reels);
    }

    this.iniciarAnimacaoPeriodica();
    this.mostrarConfigModal.set(false);

    this.configAnimacaoService.salvar({
      animacaoAtivada: config.animacaoAtivada,
      intervaloAnimacao: config.intervaloAnimacao,
      duracaoAnimacao: config.duracaoAnimacao,
      video1Url: apiUrls.video1Url,
      video2Url: apiUrls.video2Url,
      reels: config.reels,
    }).subscribe({
      error: () =>
        this.notificationService.erro(
          'Configuração aplicada só nesta tela: não foi possível salvar no servidor.'
        ),
    });
  }

  private iniciarAnimacaoPeriodica() {
    // Limpar intervalo anterior se existir
    if (this.animacaoPeriodicaInterval) {
      clearInterval(this.animacaoPeriodicaInterval);
      this.animacaoPeriodicaInterval = null;
    }

    const config = this.animations.animacaoConfig();

    // Só iniciar se animação automática estiver ativada
    if (!config.animacaoAtivada) {
      return;
    }

    // Converter intervaloAnimacao de segundos para milissegundos
    const intervaloMs = config.intervaloAnimacao * 1000;

    // Iniciar intervalo periódico fora da zona Angular para não bloquear estabilidade
    this.ngZone.runOutsideAngular(() => {
      this.animacaoPeriodicaInterval = setInterval(() => {
        // Executa dentro da zona Angular para trigger change detection
        this.ngZone.run(() => {
          // Verificar se ainda está ativada (pode ter mudado)
          const configAtual = this.animations.animacaoConfig();
          if (!configAtual.animacaoAtivada) {
            // Se foi desativada, parar o intervalo
            if (this.animacaoPeriodicaInterval) {
              clearInterval(this.animacaoPeriodicaInterval);
              this.animacaoPeriodicaInterval = null;
            }
            return;
          }

          if (this.animations.mostrarInterludio()) {
            return;
          }

          this.animations.abrirInterludio();
        });
      }, intervaloMs);
    });
  }

  handleFecharConfig() {
    this.mostrarConfigModal.set(false);
  }

  readonly tickerTexto = computed(() =>
    montarTickerTexto(this.animations.animacaoConfig().reels)
  );

  readonly configAtual = computed(() => ({
    animacaoAtivada: this.animations.animacaoConfig().animacaoAtivada,
    intervaloAnimacao: this.animations.animacaoConfig().intervaloAnimacao,
    duracaoAnimacao: this.animations.animacaoConfig().duracaoAnimacao,
    reels: this.animations.animacaoConfig().reels,
    video1Url: this.animations.animacaoConfig().video1Url || null,
    video2Url: this.animations.animacaoConfig().video2Url || null
  }));
}

