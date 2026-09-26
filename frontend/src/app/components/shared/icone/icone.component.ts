import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const CIRCULO_12_9 = 'M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0';

/** Ícones de traço (24x24, currentColor) que substituem os emojis do balcão. */
const ICONES = {
  prancheta: ['M9 2h6a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z', 'M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2', 'M9 12h6M9 16h4'],
  hamburguer: ['M4 10c0-3.3 3.6-6 8-6s8 2.7 8 6z', 'M3 14c1.5 0 1.5-1 3-1s1.5 1 3 1 1.5-1 3-1 1.5 1 3 1 1.5-1 3-1 1.5 1 3 1', 'M5 17h14v1a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2z'],
  camadas: ['M12 3l9 5-9 5-9-5z', 'M3 13l9 5 9-5'],
  monitor: ['M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z', 'M8 21h8M12 17v4'],
  prato: ['M3 18h18', 'M5 18a7 7 0 0 1 14 0', 'M12 8V6M10 6h4'],
  celular: ['M9 2h6a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z', 'M11 18h2'],
  calendario: ['M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z', 'M16 2v4M8 2v4M3 10h18'],
  dinheiro: ['M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z', 'M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0', 'M6 12h.01M18 12h.01'],
  grafico: ['M3 3v18h18', 'M7 15l4-4 3 3 5-6'],
  caixa: ['M21 8l-9-5-9 5 9 5z', 'M3 8v8l9 5 9-5V8', 'M12 13v8'],
  ajustes: ['M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6'],
  cadeado: ['M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z', 'M8 11V7a4 4 0 0 1 8 0v4'],
  sair: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'M16 17l5-5-5-5M21 12H9'],
  seta: ['M5 12h14M13 6l6 6-6 6'],
  voltar: ['M19 12H5M11 18l-6-6 6-6'],
  chevronEsquerda: ['M15 6l-6 6 6 6'],
  chevronDireita: ['M9 6l6 6-6 6'],
  chevronBaixo: ['M6 9l6 6 6-6'],
  mais: ['M12 5v14M5 12h14'],
  busca: ['M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0', 'M20 20l-3.5-3.5'],
  lapis: ['M4 20h4L19 9l-4-4L4 16z', 'M13.5 6.5l4 4'],
  lixeira: ['M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14'],
  salvar: ['M5 3h11l5 5v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z', 'M7 3v5h8V3', 'M7 21v-7h10v7'],
  camera: ['M4 8h3l2-3h6l2 3h3v11H4z', 'M8.5 13a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0-7 0'],
  fechar: ['M6 6l12 12M18 6L6 18'],
  check: ['M5 12.5l4.5 4.5L19 7'],
  checkCirculo: [CIRCULO_12_9, 'M8 12.5l3 3 5-6'],
  xCirculo: [CIRCULO_12_9, 'M9 9l6 6M15 9l-6 6'],
  alerta: ['M12 3l10 18H2z', 'M12 10v4M12 17.5h.01'],
  relogio: [CIRCULO_12_9, 'M12 7v5l3 2'],
  ampulheta: ['M5 3h14M5 21h14', 'M7 3v3a5 5 0 0 0 10 0V3', 'M7 21v-3a5 5 0 0 1 10 0v3'],
  chama: ['M12 3c1 4 5 5.5 5 10a5 5 0 0 1-10 0c0-2.5 1.5-3.5 2-5 1 1.5 1.5 2 2.5 2 0-3-.5-5 .5-7z'],
  sino: ['M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z', 'M10 21h4'],
  pausar: ['M8 5v14M16 5v14'],
  iniciar: ['M7 4l13 8-13 8z'],
  nota: ['M4 4h16v12H8l-4 4z', 'M8 9h8M8 12h5'],
  atualizar: ['M20 11a8 8 0 0 0-14.9-3.5M4 4v4h4', 'M4 13a8 8 0 0 0 14.9 3.5M20 20v-4h-4'],
  impressora: ['M6 9V3h12v6', 'M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2', 'M6 14h12v7H6z'],
} as const satisfies Record<string, readonly string[]>;

export type NomeIcone = keyof typeof ICONES;

@Component({
  selector: 'app-icone',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
      stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
      @for (d of caminhos(); track $index) {
        <path [attr.d]="d" />
      }
    </svg>
  `,
  styles: [`
    :host { display: inline-flex; flex-shrink: 0; width: 1.15em; height: 1.15em; vertical-align: -0.2em; }
    svg { width: 100%; height: 100%; }
  `]
})
export class IconeComponent {
  readonly nome = input.required<NomeIcone>();
  readonly caminhos = computed(() => ICONES[this.nome()]);
}
