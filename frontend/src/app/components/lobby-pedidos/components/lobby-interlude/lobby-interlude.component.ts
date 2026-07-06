import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  NgZone,
  OnDestroy,
  output,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { LobbyReelItem, LobbyTema } from '../../models/lobby-ui.types';
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
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LobbyInterludeComponent implements OnDestroy {
  private readonly ngZone = inject(NgZone);

  readonly ativo = input(false);
  readonly duracaoImagemSegundos = input(4);
  readonly reels = input<LobbyReelItem[]>(REELS_PADRAO);
  readonly permitirFechar = input(false);
  readonly tema = input<LobbyTema>('escuro');

  readonly onFechar = output<void>();

  readonly slideIndex = signal(0);
  readonly barDurationMs = signal(4000);
  readonly barPronta = signal(false);

  private slideTimeout: ReturnType<typeof setTimeout> | null = null;
  private transicaoTimeout: ReturnType<typeof setTimeout> | null = null;
  private sessaoAberta = 0;
  private slideEncerrado = false;

  readonly reelsAtivos = computed(() => normalizarReels(this.reels()));

  readonly slideAtual = computed(() => {
    const lista = this.reelsAtivos();
    return lista[this.slideIndex()] ?? lista[0];
  });

  readonly barDurationCss = computed(() => `${this.barDurationMs() / 1000}s`);

  readonly stackOffset = computed(() => this.slideIndex());

  constructor() {
    effect(() => {
      const aberto = this.ativo();
      this.limparTimers();
      this.slideEncerrado = false;

      if (!aberto) {
        this.slideIndex.set(0);
        this.barPronta.set(false);
        return;
      }

      this.sessaoAberta += 1;
      const sessao = this.sessaoAberta;
      this.slideIndex.set(0);
      this.barDurationMs.set(Math.max(2000, this.duracaoImagemSegundos() * 1000));
      this.barPronta.set(false);

      queueMicrotask(() => {
        if (this.sessaoAberta === sessao && this.ativo()) {
          this.iniciarSlideAtual();
        }
      });
    });
  }

  ngOnDestroy(): void {
    this.limparTimers();
  }

  fechar(): void {
    this.limparTimers();
    this.onFechar.emit();
  }

  indiceBarraPreenchida(indice: number): boolean {
    return indice < this.slideIndex();
  }

  indiceBarraAtiva(indice: number): boolean {
    return indice === this.slideIndex();
  }

  onVideoMetadata(event: Event): void {
    const video = event.target as HTMLVideoElement;
    if (!Number.isFinite(video.duration) || video.duration <= 0) {
      this.prepararSlidePorTempo(this.duracaoImagemSegundos() * 1000);
      return;
    }

    const ms = Math.max(500, Math.ceil(video.duration * 1000));
    this.prepararSlidePorTempo(ms);
    void video.play().catch(() => undefined);
  }

  onVideoEnded(): void {
    this.encerrarSlideAtual();
  }

  onVideoErro(): void {
    this.prepararSlidePorTempo(this.duracaoImagemSegundos() * 1000);
  }

  onBarAnimationEnd(indice: number, event: AnimationEvent): void {
    if (event.animationName !== 'lobby-fill-bar') return;
    if (!this.indiceBarraAtiva(indice)) return;
    if (tipoMidiaReel(this.slideAtual()) === 'video') return;
    this.encerrarSlideAtual();
  }

  private iniciarSlideAtual(): void {
    this.slideEncerrado = false;
    this.limparSlideTimeout();
    this.barPronta.set(false);

    const reel = this.slideAtual();
    if (!reel) {
      this.fechar();
      return;
    }

    const tipo = tipoMidiaReel(reel);
    if (tipo === 'video') {
      return;
    }

    const ms = Math.max(2000, this.duracaoImagemSegundos() * 1000);
    this.prepararSlidePorTempo(ms);
  }

  private prepararSlidePorTempo(ms: number): void {
    this.barDurationMs.set(ms);
    this.barPronta.set(false);

    requestAnimationFrame(() => {
      this.barPronta.set(true);
      this.agendarFallback(ms + 300);
    });
  }

  private agendarFallback(ms: number): void {
    this.limparSlideTimeout();
    this.ngZone.runOutsideAngular(() => {
      this.slideTimeout = setTimeout(() => {
        this.ngZone.run(() => this.encerrarSlideAtual());
      }, ms);
    });
  }

  private encerrarSlideAtual(): void {
    if (this.slideEncerrado || !this.ativo()) return;
    this.slideEncerrado = true;
    this.limparSlideTimeout();
    this.avancarSlide();
  }

  private avancarSlide(): void {
    const lista = this.reelsAtivos();
    const proximo = this.slideIndex() + 1;

    if (proximo >= lista.length) {
      this.fechar();
      return;
    }

    this.slideIndex.set(proximo);
    this.barPronta.set(false);

    this.transicaoTimeout = setTimeout(() => {
      this.iniciarSlideAtual();
    }, TRANSICAO_REEL_MS);
  }

  private limparSlideTimeout(): void {
    if (this.slideTimeout) {
      clearTimeout(this.slideTimeout);
      this.slideTimeout = null;
    }
  }

  private limparTimers(): void {
    this.limparSlideTimeout();
    if (this.transicaoTimeout) {
      clearTimeout(this.transicaoTimeout);
      this.transicaoTimeout = null;
    }
  }
}
