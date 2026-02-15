import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { MeioPagamento, MeioPagamentoPedido } from '../../../../services/pedido.service';

@Component({
  selector: 'app-meios-pagamento',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './meios-pagamento.component.html',
  styleUrl: './meios-pagamento.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MeiosPagamentoComponent {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  readonly valorTotal = input.required<number>();
  readonly meiosPagamento = input.required<MeioPagamentoPedido[]>();
  readonly onMeiosPagamentoChange = output<MeioPagamentoPedido[]>();

  readonly meioPagamentoSelecionado = signal<MeioPagamento | null>(null);
  readonly valorMeioPagamento = signal<number>(0);
  readonly valorPagoDinheiro = signal<number>(0);
  readonly MeioPagamento = MeioPagamento;

  // Flag para controlar se o usuário está editando manualmente
  private usuarioEditando = false;

  readonly valorRestante = computed(() => {
    return this.valorTotal() - this.calcularTotalMeiosPagamento();
  });

  readonly trocoCalculado = computed(() => {
    if (this.meioPagamentoSelecionado() !== MeioPagamento.DINHEIRO) return 0;
    const valorPago = this.valorPagoDinheiro();
    const valor = this.valorMeioPagamento();
    if (valorPago > valor && valor > 0) {
      return valorPago - valor;
    }
    return 0;
  });

  readonly isDinheiro = computed(() => {
    return this.meioPagamentoSelecionado() === MeioPagamento.DINHEIRO;
  });

  constructor() {
    if (this.isBrowser) {
      effect(
        () => {
          const restante = this.valorRestante();
          const meioSelecionado = this.meioPagamentoSelecionado();
          const valorAtual = this.valorMeioPagamento();

          // Só ajusta automaticamente se o usuário NÃO está editando
          if (!this.usuarioEditando) {
            if (meioSelecionado && restante > 0) {
              // Só ajusta se o valor excede o restante (não quando é 0)
              if (valorAtual > restante) {
                setTimeout(() => {
                  this.valorMeioPagamento.set(restante);
                }, 0);
              }
            } else if (restante <= 0) {
              setTimeout(() => {
                this.valorMeioPagamento.set(0);
              }, 0);
            }
          }
        },
        { allowSignalWrites: true }
      );
    }
  }

  // Métodos para controlar estado de edição
  onInputFocus(): void {
    this.usuarioEditando = true;
  }

  onInputBlur(): void {
    this.usuarioEditando = false;
    // Se o valor excede o restante, ajusta para o restante
    const valorAtual = this.valorMeioPagamento();
    const restante = this.valorRestante();
    if (valorAtual > restante && restante > 0) {
      this.valorMeioPagamento.set(restante);
    }
  }

  calcularTotalMeiosPagamento(): number {
    return this.meiosPagamento().reduce((total, mp) => total + mp.valor, 0);
  }

  onMeioPagamentoSelecionado(): void {
    const valorRestante = this.valorRestante();
    if (valorRestante > 0) {
      this.valorMeioPagamento.set(valorRestante);
    } else {
      this.valorMeioPagamento.set(0);
    }
    this.valorPagoDinheiro.set(0);
  }

  adicionarMeioPagamento(): void {
    const meioPagamento = this.meioPagamentoSelecionado();
    const valor = this.valorMeioPagamento();

    if (!meioPagamento) {
      return;
    }

    if (valor <= 0) {
      if (this.isBrowser) {
        alert('O valor deve ser maior que zero.');
      }
      return;
    }

    const valorRestante = this.valorRestante();

    if (valor > valorRestante) {
      if (this.isBrowser) {
        alert(`O valor não pode ser maior que o restante (${this.formatarPreco(valorRestante)}).`);
      }
      return;
    }

    const novoMeioPagamento: MeioPagamentoPedido = {
      meioPagamento: meioPagamento,
      valor: valor,
    };

    // Se for dinheiro com valor pago informado, calcula troco
    if (meioPagamento === MeioPagamento.DINHEIRO && this.valorPagoDinheiro() > valor) {
      novoMeioPagamento.valorPagoDinheiro = this.valorPagoDinheiro();
      novoMeioPagamento.troco = this.valorPagoDinheiro() - valor;
    }

    const novosMeiosPagamento = [...this.meiosPagamento(), novoMeioPagamento];
    this.onMeiosPagamentoChange.emit(novosMeiosPagamento);
    this.meioPagamentoSelecionado.set(null);
    this.valorMeioPagamento.set(0);
    this.valorPagoDinheiro.set(0);
  }

  removerMeioPagamento(index: number): void {
    const novosMeiosPagamento = this.meiosPagamento().filter((_, i) => i !== index);
    this.onMeiosPagamentoChange.emit(novosMeiosPagamento);
  }

  parseValorMeioPagamento(value: string): number {
    const parsed = parseFloat(value);
    return isNaN(parsed) ? 0 : parsed;
  }

  formatarPreco(preco: number): string {
    return `R$ ${preco.toFixed(2).replace('.', ',')}`;
  }

  formatarNomeMeioPagamento(meioPagamento: MeioPagamento): string {
    const nomes: Record<MeioPagamento, string> = {
      [MeioPagamento.PIX]: 'Pix',
      [MeioPagamento.CARTAO_CREDITO]: 'Crédito',
      [MeioPagamento.CARTAO_DEBITO]: 'Débito',
      [MeioPagamento.VALE_REFEICAO]: 'Voucher',
      [MeioPagamento.DINHEIRO]: 'Dinheiro',
    };
    return nomes[meioPagamento] || meioPagamento;
  }
}
