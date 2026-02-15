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
    const valorDigitado = this.valorMeioPagamento();
    const valorPagoNota = this.valorPagoDinheiro();
    const restante = this.valorRestante();

    // Se o operador digitou valor acima do restante no campo principal,
    // o valor digitado É o dinheiro entregue pelo cliente
    if (valorDigitado > restante && restante > 0) {
      return valorDigitado - restante;
    }

    // Fluxo via campo separado "valor pago em nota"
    if (valorPagoNota > valorDigitado && valorDigitado > 0) {
      return valorPagoNota - valorDigitado;
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
              // Só ajusta se o valor excede o restante E NÃO é dinheiro
              // (dinheiro permite valor acima do restante – é o valor real entregue pelo cliente)
              if (valorAtual > restante && meioSelecionado !== MeioPagamento.DINHEIRO) {
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
    // Se o valor excede o restante, ajusta para o restante (exceto dinheiro)
    const valorAtual = this.valorMeioPagamento();
    const restante = this.valorRestante();
    if (
      this.meioPagamentoSelecionado() !== MeioPagamento.DINHEIRO &&
      valorAtual > restante &&
      restante > 0
    ) {
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
    const valorDigitado = this.valorMeioPagamento();

    if (!meioPagamento) {
      return;
    }

    if (valorDigitado <= 0) {
      if (this.isBrowser) {
        alert('O valor deve ser maior que zero.');
      }
      return;
    }

    const valorRestante = this.valorRestante();

    if (meioPagamento === MeioPagamento.DINHEIRO) {
      // Dinheiro: permite valor acima do restante (é o valor real entregue pelo cliente)
      const valorPedido = Math.min(valorDigitado, valorRestante);

      const novoMeioPagamento: MeioPagamentoPedido = {
        meioPagamento: meioPagamento,
        valor: valorPedido,
      };

      if (valorDigitado > valorRestante) {
        // Valor digitado no campo principal > restante = é o valorPagoDinheiro
        novoMeioPagamento.valorPagoDinheiro = valorDigitado;
        novoMeioPagamento.troco = valorDigitado - valorPedido;
      } else if (this.valorPagoDinheiro() > valorDigitado) {
        // Fluxo via campo separado "valor pago em nota"
        novoMeioPagamento.valorPagoDinheiro = this.valorPagoDinheiro();
        novoMeioPagamento.troco = this.valorPagoDinheiro() - valorDigitado;
      }

      const novosMeiosPagamento = [...this.meiosPagamento(), novoMeioPagamento];
      this.onMeiosPagamentoChange.emit(novosMeiosPagamento);
    } else {
      // Outros meios: bloqueia valor acima do restante
      if (valorDigitado > valorRestante) {
        if (this.isBrowser) {
          alert(
            `O valor não pode ser maior que o restante (${this.formatarPreco(valorRestante)}).`
          );
        }
        return;
      }

      const novoMeioPagamento: MeioPagamentoPedido = {
        meioPagamento: meioPagamento,
        valor: valorDigitado,
      };

      const novosMeiosPagamento = [...this.meiosPagamento(), novoMeioPagamento];
      this.onMeiosPagamentoChange.emit(novosMeiosPagamento);
    }

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
