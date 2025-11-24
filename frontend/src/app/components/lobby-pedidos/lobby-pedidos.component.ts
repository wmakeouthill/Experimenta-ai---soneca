import { Component, OnInit, OnDestroy, inject, signal, computed, effect, ChangeDetectionStrategy, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { StatusPedido, Pedido } from '../../services/pedido.service';
import { useLobbyPedidos } from './composables/use-lobby-pedidos';
import { useAnimations } from './composables/use-animations';
import { SurferAnimationComponent } from './components/surfer-animation/surfer-animation.component';
import { OrderListComponent } from './components/order-list/order-list.component';
import { LobbyHeaderComponent } from './components/header/header.component';
import { ConfigAnimacaoModalComponent, ConfigAnimacao } from './components/config-animacao-modal/config-animacao-modal.component';
import { ConfigAnimacaoService } from '../../services/config-animacao.service';

@Component({
  selector: 'app-lobby-pedidos',
  standalone: true,
  imports: [
    CommonModule,
    LobbyHeaderComponent,
    SurferAnimationComponent,
    OrderListComponent,
    ConfigAnimacaoModalComponent
  ],
  templateUrl: './lobby-pedidos.component.html',
  styleUrl: './lobby-pedidos.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LobbyPedidosComponent implements OnInit, OnDestroy {
  private readonly configAnimacaoService = inject(ConfigAnimacaoService);
  private readonly platformId = inject(PLATFORM_ID);
  
  readonly pedidosAnteriores = signal<Pedido[]>([]);
  
  readonly lobbyPedidos = useLobbyPedidos();
  readonly animations = useAnimations();

  readonly isAnimating = computed(() => this.animations.isAnimating());
  readonly mostrarConfigModal = signal<boolean>(false);
  
  // Expor StatusPedido para o template
  readonly StatusPedido = StatusPedido;
  
  private pollingInterval: any = null;
  private readonly intervaloPolling = 3000; // 3 segundos

  private get isBrowser(): boolean {
    return isPlatformBrowser(this.platformId);
  }

  constructor() {
    // Effect apenas no browser (não durante SSR)
    if (isPlatformBrowser(this.platformId)) {
      effect(() => {
        const pedidosAtuais = this.lobbyPedidos.pedidos();
        const pedidosAnt = this.pedidosAnteriores();
        
        if (pedidosAtuais.length !== pedidosAnt.length || 
            pedidosAtuais.some((p, i) => p.id !== pedidosAnt[i]?.id || p.status !== pedidosAnt[i]?.status)) {
          this.verificarMudancas(pedidosAnt, pedidosAtuais);
          this.pedidosAnteriores.set([...pedidosAtuais]);
        }
      }, { allowSignalWrites: true });
    }
  }

  ngOnInit() {
    // Executar apenas no browser (não durante SSR)
    if (this.isBrowser) {
      this.lobbyPedidos.carregarSessaoAtiva();
      this.lobbyPedidos.carregarPedidos();
      this.iniciarPolling();
      this.carregarConfigAnimacao();
    }
  }

  private carregarConfigAnimacao() {
    this.configAnimacaoService.carregar().subscribe({
      next: (config) => {
        this.animations.animacaoConfig.set({
          animacaoAtivada: config.animacaoAtivada,
          intervaloAnimacao: config.intervaloAnimacao,
          duracaoAnimacao: config.duracaoAnimacao
        });
      },
      error: () => {
        // Usar valores padrão se não conseguir carregar
        console.warn('Não foi possível carregar configuração de animação, usando valores padrão');
      }
    });
  }

  ngOnDestroy() {
    if (this.pollingInterval) {
      clearInterval(this.pollingInterval);
    }
  }

  private iniciarPolling() {
    this.pollingInterval = setInterval(() => {
      this.lobbyPedidos.carregarPedidos();
    }, this.intervaloPolling);
  }

  private verificarMudancas(anteriores: Pedido[], atuais: Pedido[]) {
    for (const atual of atuais) {
      const anterior = anteriores.find(p => p.id === atual.id);
      if (anterior && anterior.status === StatusPedido.PREPARANDO && atual.status === StatusPedido.PRONTO) {
        this.animations.animarTransicaoStatus(atual, anterior.status, this.animations.animacaoConfig().duracaoAnimacao);
        break;
      }
    }
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
    // Disparar animação manualmente se houver pedidos
    const pedidos = this.lobbyPedidos.pedidos();
    const pedidosPreparando = pedidos.filter(p => p.status === StatusPedido.PREPARANDO);
    const pedidosPronto = pedidos.filter(p => p.status === StatusPedido.PRONTO);
    
    if (pedidosPreparando.length > 0 && pedidosPronto.length > 0) {
      // Animar o primeiro pedido pronto
      const pedidoPronto = pedidosPronto[0];
      this.animations.animarTransicaoStatus(
        pedidoPronto,
        StatusPedido.PREPARANDO,
        this.animations.animacaoConfig().duracaoAnimacao
      );
    }
  }

  handleAbrirConfig() {
    this.mostrarConfigModal.set(true);
  }

  handleSalvarConfig(config: ConfigAnimacao) {
    // Atualizar configuração local
    this.animations.animacaoConfig.set({
      animacaoAtivada: config.animacaoAtivada,
      intervaloAnimacao: config.intervaloAnimacao,
      duracaoAnimacao: config.duracaoAnimacao
    });
    
    // Salvar no backend
    this.configAnimacaoService.salvar(config).subscribe({
      next: () => {
        console.log('Configuração de animação salva com sucesso');
        this.mostrarConfigModal.set(false);
      },
      error: (error) => {
        console.error('Erro ao salvar configuração:', error);
        alert('Erro ao salvar configuração. Tente novamente.');
      }
    });
  }

  handleFecharConfig() {
    this.mostrarConfigModal.set(false);
  }

  readonly configAtual = computed(() => ({
    animacaoAtivada: this.animations.animacaoConfig().animacaoAtivada,
    intervaloAnimacao: this.animations.animacaoConfig().intervaloAnimacao,
    duracaoAnimacao: this.animations.animacaoConfig().duracaoAnimacao,
    video1Url: null,
    video2Url: null
  }));
}

