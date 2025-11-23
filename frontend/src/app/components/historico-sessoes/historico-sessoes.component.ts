import { Component, inject, PLATFORM_ID, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { RouterModule } from '@angular/router';
import { useHistoricoSessoes } from './composables/use-historico-sessoes';
import { SessaoTrabalho, StatusSessao } from '../../services/sessao-trabalho.service';
import { Pedido, MeioPagamento } from '../../services/pedido.service';
import { FormatoUtil } from '../../utils/formato.util';

/**
 * Componente de apresentação para histórico de sessões.
 * Responsabilidade única: exibir UI e delegar lógica para o composable.
 */
@Component({
  selector: 'app-historico-sessoes',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './historico-sessoes.component.html',
  styleUrl: './historico-sessoes.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class HistoricoSessoesComponent implements OnInit {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  readonly historicoComposable = useHistoricoSessoes();
  readonly StatusSessao = StatusSessao;
  readonly MeioPagamento = MeioPagamento;

  // Expor propriedades do composable
  readonly sessoes = this.historicoComposable.sessoes;
  readonly sessaoSelecionada = this.historicoComposable.sessaoSelecionada;
  readonly pedidos = this.historicoComposable.pedidos;
  readonly estado = this.historicoComposable.estado;
  readonly erro = this.historicoComposable.erro;
  readonly estaCarregando = this.historicoComposable.estaCarregando;
  readonly temSessaoSelecionada = this.historicoComposable.temSessaoSelecionada;
  readonly temPedidos = this.historicoComposable.temPedidos;
  readonly resumoFaturamento = this.historicoComposable.resumoFaturamento;

  ngOnInit(): void {
    if (this.isBrowser) {
      this.historicoComposable.carregarSessoes();
    }
  }

  selecionarSessao(sessao: SessaoTrabalho): void {
    this.historicoComposable.selecionarSessao(sessao);
  }

  limparSelecao(): void {
    this.historicoComposable.selecionarSessao(null);
  }

  recarregar(): void {
    this.historicoComposable.recarregar();
  }

  formatarData(data: string | undefined): string {
    if (!data) return '';
    return FormatoUtil.dataHora(data);
  }

  formatarMoeda(valor: number): string {
    return FormatoUtil.moeda(valor);
  }

  obterNomeMeioPagamento(meio: MeioPagamento): string {
    const nomes: Record<MeioPagamento, string> = {
      [MeioPagamento.PIX]: 'PIX',
      [MeioPagamento.CARTAO_CREDITO]: 'Cartão de Crédito',
      [MeioPagamento.CARTAO_DEBITO]: 'Cartão de Débito',
      [MeioPagamento.VALE_REFEICAO]: 'Vale Refeição',
      [MeioPagamento.DINHEIRO]: 'Dinheiro'
    };
    return nomes[meio] || meio;
  }
}

