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
import { DistribuicaoMeioPagamento } from '../../../../models/relatorios.model';
import {
  corPorIndice,
  defaultChartOptions,
  destruirChart,
  renderizarChart
} from '../../../../utils/chart.util';

@Component({
  selector: 'app-meios-pagamento-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './meios-pagamento-chart.component.html',
  styleUrl: './meios-pagamento-chart.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MeiosPagamentoChartComponent {
  private readonly destroyRef = inject(DestroyRef);

  readonly dados = input.required<DistribuicaoMeioPagamento[]>();

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

  private montarConfiguracao(dados: DistribuicaoMeioPagamento[]): ChartConfiguration<'doughnut'> {
    const labels = dados.map(item => item.meioPagamento);
    const valores = dados.map(item => item.valorTotal);

    const options: ChartOptions<'doughnut'> = {
      ...defaultChartOptions,
      plugins: {
        ...defaultChartOptions.plugins,
        legend: {
          ...defaultChartOptions.plugins?.legend,
          position: 'right'
        }
      }
    };

    return {
      type: 'doughnut',
      data: {
        labels,
        datasets: [
          {
            label: 'Faturamento',
            data: valores,
            backgroundColor: labels.map((_, indice) => corPorIndice(indice)),
            borderColor: '#1d1e2a'
          }
        ]
      },
      options
    };
  }
}

