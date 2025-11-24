import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  ViewChild,
  effect,
  inject,
  input
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Chart, ChartConfiguration, ChartOptions } from 'chart.js';
import { ProdutoMaisVendido } from '../../../../models/relatorios.model';
import {
  corPorIndice,
  defaultChartOptions,
  destruirChart,
  renderizarChart
} from '../../../../utils/chart.util';

@Component({
  selector: 'app-top-produtos-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './top-produtos-chart.component.html',
  styleUrl: './top-produtos-chart.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TopProdutosChartComponent {
  private readonly destroyRef = inject(DestroyRef);

  readonly dados = input.required<ProdutoMaisVendido[]>();

  @ViewChild('canvas')
  set canvasRef(elemento: ElementRef<HTMLCanvasElement> | undefined) {
    this.canvas = elemento;
    this.renderizar();
  }

  private canvas?: ElementRef<HTMLCanvasElement>;
  private chart: Chart | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => destruirChart(this.chart));

    effect(() => {
      this.dados();
      this.renderizar();
    });
  }

  private renderizar(): void {
    if (!this.canvas) {
      return;
    }

    const dados = this.dados();
    if (dados.length === 0) {
      destruirChart(this.chart);
      this.chart = null;
      return;
    }

    const configuracao = this.montarConfiguracao(dados);
    this.chart = renderizarChart(this.canvas.nativeElement, configuracao, this.chart);
  }

  private montarConfiguracao(dados: ProdutoMaisVendido[]): ChartConfiguration<'bar'> {
    const labels = dados.map(item => item.produtoNome);
    const quantidades = dados.map(item => item.quantidadeVendida);

    const options: ChartOptions<'bar'> = {
      ...defaultChartOptions,
      plugins: {
        ...defaultChartOptions.plugins,
        legend: { display: false }
      },
      scales: {
        x: {
          type: 'category',
          ticks: { color: '#d4d4d8', autoSkip: false },
          grid: { display: false }
        },
        y: {
          type: 'linear',
          beginAtZero: true,
          ticks: { color: '#a1a1aa', precision: 0 },
          grid: { color: 'rgba(255,255,255,0.08)' }
        }
      }
    };

    return {
      type: 'bar',
      data: {
        labels,
        datasets: [
          {
            label: 'Quantidade vendida',
            data: quantidades,
            backgroundColor: labels.map((_, indice) => corPorIndice(indice)),
            borderRadius: 6
          }
        ]
      },
      options
    };
  }
}

