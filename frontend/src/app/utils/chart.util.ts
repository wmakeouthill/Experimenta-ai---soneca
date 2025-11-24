import { Chart, ChartConfiguration, ChartOptions, registerables, TooltipItem } from 'chart.js';

Chart.register(...registerables);

const palette = [
  '#08BDBD',
  '#F25F5C',
  '#FFE066',
  '#247BA0',
  '#70C1B3',
  '#5D3FD3',
  '#FF9F1C',
  '#2EC4B6',
  '#CBF3F0',
  '#FFBF69',
  '#1B998B',
  '#ED6A5A'
];

export const defaultChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      position: 'bottom',
      labels: {
        usePointStyle: true,
        color: '#d4d4d8'
      }
    },
    tooltip: {
      callbacks: {
        label: (context: TooltipItem<'line' | 'bar' | 'doughnut'>) => {
          const label = context.dataset.label ?? '';
          const value = context.parsed;
          return `${label}: ${formatarValor(value)}`;
        }
      }
    }
  }
} satisfies Pick<ChartOptions, 'responsive' | 'maintainAspectRatio' | 'plugins'>;

export function corPorIndice(indice: number): string {
  return palette[indice % palette.length];
}

export function formatarValor(valor: number): string {
  return valor.toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    maximumFractionDigits: 2
  });
}

export function renderizarChart(
  canvas: HTMLCanvasElement,
  configuracao: ChartConfiguration,
  chartAtual: Chart | null
): Chart {
  chartAtual?.destroy();
  return new Chart(canvas, configuracao);
}

export function destruirChart(chart: Chart | null): void {
  chart?.destroy();
}

