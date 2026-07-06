import {
  Component,
  computed,
  effect,
  inject,
  input,
  NgZone,
  OnDestroy,
  output,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { LobbyReelItem, LobbyReelMidiaTipo, LobbyTema } from '../../models/lobby-ui.types';
import {
  normalizarReels,
  REELS_PADRAO,
  tipoMidiaReel,
  TRANSICAO_REEL_MS,
} from '../../utils/lobby-promocoes.util';

@Component({
  selector: 'app-lobby-interlude',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './lobby-interlude.component.html',
  styleUrl: './lobby-interlude.component.css',
})
export class LobbyInterludeComponent implements OnDestroy {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly ngZone = inject(NgZone);

  readonly ativo = input(false);
  readonly duracaoImagemSegundos = input(4);
  readonly reels = input<LobbyReelItem[]>(REELS_PADRAO);
  readonly permitirFechar = input(false);
  readonly tema = input<LobbyTema>('escuro');

  readonly onFechar = output<void>();

  readonly slideIndex = signal(0);
  readonly barProgresso = signal(0);
  /** Incrementa a cada slide para reiniciar animação CSS da barra. */
  readonly cicloBarra = signal(0);

  readonly reelsAtivos = computed(() => normalizarReels(this.reels()));

  readonly slideAtual = computed(() => {
    const lista = this.reelsAtivos();
    return lista[this.slideIndex()] ?? lista[0];
  });

  readonly tipoSlideAtual = computed((): LobbyReelMidiaTipo => {
    const reel = this.slideAtual();
    return reel ? tipoMidiaReel(reel) : 'texto';
  });

  readonly stackOffset = computed(() => this.slideIndex());

  readonly duracaoBarraCss = computed(() => {
    const segundos = Math.max(2, this.duracaoImagemSegundos());
    return `${segundos}s`;
  });

  private slideTimeout: ReturnType<typeof setTimeout> | null = null;
  private transicaoTimeout: ReturnType<typeof setTimeout> | null = null;
  private inicioTimeout: ReturnType<typeof setTimeout> | null = null;
  private videoFallbackTimeout: ReturnType<typeof setTimeout> | null = null;
  private sessaoAberta = 0;
  private slideEncerrado = false;

  constructor() {
    effect(() => {
      const aberto = this.ativo();

      if (!aberto) {
        this.resetarSessao();
        return;
      }

      if (!isPlatformBrowser(this.platformId)) return;

      this.iniciarSessao();
    }, { allowSignalWrites: true });
  }

  ngOnDestroy(): void {
    this.resetarSessao();
  }

  fechar(): void {
    this.resetarSessao();
    this.onFechar.emit();
  }

  onBarAnimacaoFim(event: AnimationEvent): void {
    if (event.animationName !== 'lobby-interlude-progress') return;
    this.ngZone.run(() => this.encerrarSlideAtual());
  }

  onVideoLoaded(event: Event): void {
    const video = event.target as HTMLVideoElement;
    this.ngZone.run(() => {
      this.limparVideoFallback();
      this.barProgresso.set(0);
      void video.play().catch(() => this.agendarFallbackVideo());
    });
  }

  onVideoTimeUpdate(event: Event): void {
    const video = event.target as HTMLVideoElement;
    if (!Number.isFinite(video.duration) || video.duration <= 0) return;
    this.ngZone.run(() => {
      this.barProgresso.set(Math.min(100, (video.currentTime / video.duration) * 100));
    });
  }

  onVideoEnded(): void {
    this.ngZone.run(() => {
      this.barProgresso.set(100);
      this.encerrarSlideAtual();
    });
  }

  onVideoErro(): void {
    this.ngZone.run(() => this.iniciarFallbackVideo());
  }

  private iniciarSessao(): void {
    this.limparTimers();
    this.slideEncerrado = false;
    this.sessaoAberta += 1;
    const sessao = this.sessaoAberta;
    this.slideIndex.set(0);
    this.barProgresso.set(0);
    this.cicloBarra.set(0);

    this.inicioTimeout = setTimeout(() => {
      if (this.sessaoAberta === sessao && this.ativo()) {
        this.ngZone.run(() => this.iniciarSlideAtual());
      }
    }, 80);
  }

  private resetarSessao(): void {
    this.limparTimers();
    this.slideEncerrado = false;
    this.slideIndex.set(0);
    this.barProgresso.set(0);
    this.cicloBarra.set(0);
  }

  private iniciarSlideAtual(): void {
    this.slideEncerrado = false;
    this.limparSlideTimeout();
    this.limparVideoFallback();
    this.barProgresso.set(0);
    this.cicloBarra.update((n) => n + 1);

    const reel = this.slideAtual();
    if (!reel) {
      this.fechar();
      return;
    }

    const tipo = tipoMidiaReel(reel);

    if (tipo === 'video') {
      this.agendarFallbackVideo();
      return;
    }

    const ms = Math.max(2000, this.duracaoImagemSegundos() * 1000);
    this.slideTimeout = setTimeout(() => {
      if (!this.slideEncerrado && this.ativo()) {
        this.ngZone.run(() => {
          this.barProgresso.set(100);
          this.encerrarSlideAtual();
        });
      }
    }, ms + 200);
  }

  private agendarFallbackVideo(): void {
    this.limparVideoFallback();
    const ms = Math.max(3000, this.duracaoImagemSegundos() * 1000);

    this.videoFallbackTimeout = setTimeout(() => {
      if (this.slideEncerrado || !this.ativo() || this.tipoSlideAtual() !== 'video') return;
      if (this.barProgresso() < 2) {
        this.ngZone.run(() => this.iniciarFallbackVideo());
      }
    }, 2500);

    this.slideTimeout = setTimeout(() => {
      if (!this.slideEncerrado && this.ativo() && this.tipoSlideAtual() === 'video') {
        this.ngZone.run(() => {
          this.barProgresso.set(100);
          this.encerrarSlideAtual();
        });
      }
    }, ms + 500);
  }

  private iniciarFallbackVideo(): void {
    const ms = Math.max(3000, this.duracaoImagemSegundos() * 1000);
    this.limparVideoFallback();
    this.barProgresso.set(0);

    const inicio = Date.now();
    const tick = () => {
      if (this.slideEncerrado || !this.ativo() || this.tipoSlideAtual() !== 'video') return;
      const pct = Math.min(100, ((Date.now() - inicio) / ms) * 100);
      this.barProgresso.set(pct);
      if (pct >= 100) {
        this.encerrarSlideAtual();
        return;
      }
      this.videoFallbackTimeout = setTimeout(tick, 40);
    };

    this.ngZone.runOutsideAngular(() => {
      this.videoFallbackTimeout = setTimeout(tick, 40);
    });
  }

  private encerrarSlideAtual(): void {
    if (this.slideEncerrado || !this.ativo()) return;
    this.slideEncerrado = true;
    this.limparSlideTimeout();
    this.limparVideoFallback();
    this.avancarSlide();
  }

  private avancarSlide(): void {
    const proximo = this.slideIndex() + 1;

    if (proximo >= this.reelsAtivos().length) {
      this.fechar();
      return;
    }

    this.slideIndex.set(proximo);
    this.barProgresso.set(0);

    this.transicaoTimeout = setTimeout(() => {
      if (this.ativo()) {
        this.ngZone.run(() => this.iniciarSlideAtual());
      }
    }, TRANSICAO_REEL_MS);
  }

  private limparSlideTimeout(): void {
    if (this.slideTimeout) {
      clearTimeout(this.slideTimeout);
      this.slideTimeout = null;
    }
  }

  private limparVideoFallback(): void {
    if (this.videoFallbackTimeout) {
      clearTimeout(this.videoFallbackTimeout);
      this.videoFallbackTimeout = null;
    }
  }

  private limparTimers(): void {
    if (this.inicioTimeout) {
      clearTimeout(this.inicioTimeout);
      this.inicioTimeout = null;
    }
    this.limparSlideTimeout();
    this.limparVideoFallback();
    if (this.transicaoTimeout) {
      clearTimeout(this.transicaoTimeout);
      this.transicaoTimeout = null;
    }
  }
}
