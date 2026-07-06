import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  OnDestroy,
  output,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { LobbyReelItem, LobbyTema } from '../../models/lobby-ui.types';
import { normalizarReels, REELS_PADRAO } from '../../utils/lobby-promocoes.util';

@Component({
  selector: 'app-lobby-interlude',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './lobby-interlude.component.html',
  styleUrl: './lobby-interlude.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LobbyInterludeComponent implements OnDestroy {
  readonly ativo = input(false);
  readonly duracaoPorReelSegundos = input(4);
  readonly reels = input<LobbyReelItem[]>(REELS_PADRAO);
  readonly permitirFechar = input(false);
  readonly tema = input<LobbyTema>('escuro');

  readonly onFechar = output<void>();

  readonly slideIndex = signal(0);

  private slideInterval: ReturnType<typeof setInterval> | null = null;
  private closeTimeout: ReturnType<typeof setTimeout> | null = null;

  readonly reelsAtivos = computed(() => normalizarReels(this.reels()));

  readonly slideAtual = computed(() => {
    const lista = this.reelsAtivos();
    return lista[this.slideIndex()] ?? lista[0];
  });

  readonly duracaoSlideMs = computed(() => Math.max(2, this.duracaoPorReelSegundos()) * 1000);

  readonly duracaoTotalMs = computed(() => this.reelsAtivos().length * this.duracaoSlideMs());

  readonly videoUrlAtual = computed(() => this.slideAtual()?.videoUrl ?? null);

  constructor() {
    effect(() => {
      const aberto = this.ativo();
      const duracaoSlide = this.duracaoSlideMs();
      const duracaoTotal = this.duracaoTotalMs();
      this.limparTimers();

      if (!aberto) {
        this.slideIndex.set(0);
        return;
      }

      const total = this.reelsAtivos().length;
      this.slideIndex.set(0);

      this.slideInterval = setInterval(() => {
        this.slideIndex.update((i) => (i + 1) % Math.max(total, 1));
      }, duracaoSlide);

      this.closeTimeout = setTimeout(() => this.fechar(), duracaoTotal);
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

  duracaoBarraCss(): string {
    return `${this.duracaoPorReelSegundos()}s`;
  }

  private limparTimers(): void {
    if (this.slideInterval) {
      clearInterval(this.slideInterval);
      this.slideInterval = null;
    }
    if (this.closeTimeout) {
      clearTimeout(this.closeTimeout);
      this.closeTimeout = null;
    }
  }
}
