import {
  ChangeDetectionStrategy,
  Component,
  effect,
  input,
  OnDestroy,
  output,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { LobbySlidePromo, LobbyTema } from '../../models/lobby-ui.types';

const SLIDES_PADRAO: LobbySlidePromo[] = [
  { titulo: 'Combo Duplo Bacon — só hoje' },
  { titulo: 'Sobremesa grátis acima de R$ 60' },
  { titulo: 'Novo: Milkshake de Paçoca' },
];

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
  readonly duracaoSegundos = input(6);
  readonly video1Url = input<string | null>(null);
  readonly video2Url = input<string | null>(null);
  readonly permitirFechar = input(false);
  readonly slides = input<LobbySlidePromo[]>(SLIDES_PADRAO);
  readonly tema = input<LobbyTema>('escuro');

  readonly onFechar = output<void>();

  readonly slideIndex = signal(0);

  private slideInterval: ReturnType<typeof setInterval> | null = null;
  private closeTimeout: ReturnType<typeof setTimeout> | null = null;

  readonly slideAtual = signal<LobbySlidePromo>(SLIDES_PADRAO[0]);

  constructor() {
    effect(() => {
      const aberto = this.ativo();
      this.limparTimers();

      if (!aberto) {
        this.slideIndex.set(0);
        return;
      }

      const lista = this.slides();
      this.slideIndex.set(0);
      this.slideAtual.set(lista[0] ?? SLIDES_PADRAO[0]);

      this.slideInterval = setInterval(() => {
        this.slideIndex.update((i) => {
          const proximo = (i + 1) % Math.max(lista.length, 1);
          this.slideAtual.set(lista[proximo] ?? SLIDES_PADRAO[0]);
          return proximo;
        });
      }, 4200);

      this.closeTimeout = setTimeout(() => this.fechar(), this.duracaoSegundos() * 1000);
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

  videoAtual(): string | null {
    const idx = this.slideIndex();
    if (idx === 0 && this.video1Url()) return this.video1Url();
    if (idx === 1 && this.video2Url()) return this.video2Url();
    return this.video1Url() ?? this.video2Url();
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
