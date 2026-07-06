import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  input,
  OnDestroy,
  signal,
  viewChild,
} from '@angular/core';

/** ~72px/s — rolagem contínua estilo canal de TV */
const VELOCIDADE_TICKER_PX_S = 72;

@Component({
  selector: 'app-lobby-ticker',
  standalone: true,
  templateUrl: './lobby-ticker.component.html',
  styleUrl: './lobby-ticker.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LobbyTickerComponent implements OnDestroy {
  readonly texto = input<string>(
    'Experimenta aí do Soneca — Peça pelo app, acompanhe aqui e retire com praticidade!'
  );

  readonly marqueeRef = viewChild<ElementRef<HTMLElement>>('marquee');

  readonly segmentos = computed(() => {
    const bruto = this.texto().trim();
    if (!bruto) return ['Experimenta aí do Soneca'];
    return bruto.split('★').map((s) => s.trim()).filter(Boolean);
  });

  readonly textoMarquee = computed(() => {
    const partes = this.segmentos();
    return partes.map((p) => `★ ${p}`).join('    ');
  });

  /** Largura de um segmento em px — loop sem salto na animação */
  readonly larguraSegmentoPx = signal(0);

  readonly duracaoAnimacaoCss = computed(() => {
    const largura = this.larguraSegmentoPx();
    if (largura <= 0) return '40s';
    const segundos = Math.max(14, Math.min(120, largura / VELOCIDADE_TICKER_PX_S));
    return `${segundos}s`;
  });

  readonly deslocamentoPx = computed(() => -this.larguraSegmentoPx());

  private resizeObserver: ResizeObserver | null = null;

  constructor() {
    afterNextRender(() => this.configurarMedicao());

    effect(() => {
      this.textoMarquee();
      queueMicrotask(() => this.medirSegmento());
    });
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
  }

  private configurarMedicao(): void {
    const marquee = this.marqueeRef()?.nativeElement;
    if (!marquee) return;

    this.medirSegmento();

    this.resizeObserver = new ResizeObserver(() => this.medirSegmento());
    this.resizeObserver.observe(marquee);
    const primeiro = marquee.querySelector<HTMLElement>('.lobby-ticker__item');
    if (primeiro) {
      this.resizeObserver.observe(primeiro);
    }
  }

  private medirSegmento(): void {
    const marquee = this.marqueeRef()?.nativeElement;
    const primeiro = marquee?.querySelector<HTMLElement>('.lobby-ticker__item');
    if (!primeiro) return;

    const largura = Math.round(primeiro.getBoundingClientRect().width);
    if (largura > 0 && largura !== this.larguraSegmentoPx()) {
      this.larguraSegmentoPx.set(largura);
    }
  }
}
