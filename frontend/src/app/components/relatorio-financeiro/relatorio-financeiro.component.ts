import { ChangeDetectionStrategy, Component, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { useRelatorioFinanceiro } from './composables/use-relatorio-financeiro';
import { FormatoUtil } from '../../utils/formato.util';
import { MeioPagamento, StatusPedido } from '../../services/pedido.service';
import { TooltipItensComponent } from './components/tooltip-itens/tooltip-itens.component';
import { TooltipMeiosPagamentoComponent } from './components/tooltip-meios-pagamento/tooltip-meios-pagamento.component';

@Component({
  selector: 'app-relatorio-financeiro',
  standalone: true,
  imports: [CommonModule, RouterModule, TooltipItensComponent, TooltipMeiosPagamentoComponent],
  templateUrl: './relatorio-financeiro.component.html',
  styleUrl: './relatorio-financeiro.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RelatorioFinanceiroComponent {
  private readonly store = useRelatorioFinanceiro();

  readonly dataFiltro = this.store.dataFiltro;
  readonly estado = this.store.estado;
  readonly erro = this.store.erro;
  readonly pedidos = this.store.pedidosOrdenados;
  readonly estaCarregando = this.store.estaCarregando;
  readonly possuiDados = this.store.possuiDados;
  readonly resumoFinanceiro = this.store.resumoFinanceiro;
  readonly resumoPorMeioPagamento = this.store.resumoPorMeioPagamento;

  readonly dataFiltroInput = computed(() => this.dataFiltro());

  readonly StatusPedido = StatusPedido;
  readonly MeioPagamento = MeioPagamento;

  alterarData(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input?.value) {
      this.store.alterarData(input.value);
    }
  }

  recarregar(): void {
    this.store.carregarPedidos();
  }

  formatarMoeda(valor: number): string {
    return FormatoUtil.moeda(valor);
  }

  formatarData(data: string): string {
    if (!data) return '';
    const date = new Date(data);
    return date.toLocaleString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  formatarStatus(status: StatusPedido): string {
    const statusMap: Record<StatusPedido, string> = {
      [StatusPedido.PENDENTE]: 'Pendente',
      [StatusPedido.PREPARANDO]: 'Preparando',
      [StatusPedido.PRONTO]: 'Pronto',
      [StatusPedido.FINALIZADO]: 'Finalizado',
      [StatusPedido.CANCELADO]: 'Cancelado'
    };
    return statusMap[status] || status;
  }

  formatarMeioPagamento(meio: MeioPagamento): string {
    const nomes: Record<MeioPagamento, string> = {
      [MeioPagamento.PIX]: 'PIX',
      [MeioPagamento.CARTAO_CREDITO]: 'Cartão de Crédito',
      [MeioPagamento.CARTAO_DEBITO]: 'Cartão de Débito',
      [MeioPagamento.VALE_REFEICAO]: 'Vale Refeição',
      [MeioPagamento.DINHEIRO]: 'Dinheiro'
    };
    return nomes[meio] || meio;
  }

  obterClasseStatus(status: StatusPedido): string {
    const classes: Record<StatusPedido, string> = {
      [StatusPedido.PENDENTE]: 'status-pendente',
      [StatusPedido.PREPARANDO]: 'status-preparando',
      [StatusPedido.PRONTO]: 'status-pronto',
      [StatusPedido.FINALIZADO]: 'status-finalizado',
      [StatusPedido.CANCELADO]: 'status-cancelado'
    };
    return classes[status] || '';
  }

  obterValorPorMeioPagamento(meioPagamento: MeioPagamento): number {
    const resumo = this.resumoPorMeioPagamento();
    const item = resumo.find(r => r.meioPagamento === meioPagamento);
    return item?.valorTotal || 0;
  }

  obterQuantidadePorMeioPagamento(meioPagamento: MeioPagamento): number {
    const resumo = this.resumoPorMeioPagamento();
    const item = resumo.find(r => r.meioPagamento === meioPagamento);
    return item?.quantidadePedidos || 0;
  }

  readonly metodosPagamentoOrdenados: MeioPagamento[] = [
    MeioPagamento.DINHEIRO,
    MeioPagamento.PIX,
    MeioPagamento.CARTAO_CREDITO,
    MeioPagamento.CARTAO_DEBITO,
    MeioPagamento.VALE_REFEICAO
  ];
}

