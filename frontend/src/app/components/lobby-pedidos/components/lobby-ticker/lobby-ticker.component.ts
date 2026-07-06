import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-lobby-ticker',
  standalone: true,
  templateUrl: './lobby-ticker.component.html',
  styleUrl: './lobby-ticker.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LobbyTickerComponent {
  readonly texto = input<string>(
    'Experimenta aí do Soneca — Peça pelo app, acompanhe aqui e retire com praticidade!'
  );

  readonly segmentos = computed(() => {
    const bruto = this.texto().trim();
    if (!bruto) return ['Experimenta aí do Soneca'];
    return bruto.split('★').map((s) => s.trim()).filter(Boolean);
  });

  readonly textoMarquee = computed(() => {
    const partes = this.segmentos();
    return partes.map((p) => `★ ${p}`).join('    ');
  });

  /** Velocidade estilo canal de TV: ~10 caracteres por segundo */
  readonly duracaoAnimacaoCss = computed(() => {
    const chars = Math.max(this.textoMarquee().length, 24);
    const segundos = Math.max(16, Math.min(90, chars / 10));
    return `${segundos}s`;
  });
}
