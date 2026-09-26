import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';

export type ModoPagamentoMesa = 'PRE_PAGO' | 'POS_PAGO';

export interface ConfigPagamentoPublica {
  pagamentosAtivos: boolean;
  totem: { pixAtivo: boolean; cartaoAtivo: boolean };
  mesa: { pixAtivo: boolean; cartaoAtivo: boolean; modo: ModoPagamentoMesa };
  gatewayPixSimulado: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class PagamentoConfigService {
  private readonly http = inject(HttpClient);
  private readonly _config = signal<ConfigPagamentoPublica | null>(null);

  readonly config = this._config.asReadonly();

  readonly pixTotemAtivo = computed(() => this._config()?.totem.pixAtivo ?? false);
  readonly cartaoTotemAtivo = computed(() => this._config()?.totem.cartaoAtivo ?? false);
  readonly pixMesaAtivo = computed(() => this._config()?.mesa.pixAtivo ?? false);
  readonly cartaoMesaAtivo = computed(() => this._config()?.mesa.cartaoAtivo ?? false);
  readonly modoMesa = computed<ModoPagamentoMesa>(() => this._config()?.mesa.modo ?? 'PRE_PAGO');
  /**
   * Pre-pago so vale com PIX ou cartao digital ativo na mesa. Sem eles (pagamentos desligados
   * ou config indisponivel) a mesa segue manual: o cliente escolhe o meio e paga no atendimento.
   */
  readonly mesaPrePago = computed(
    () => this.modoMesa() === 'PRE_PAGO' && (this.pixMesaAtivo() || this.cartaoMesaAtivo())
  );
  readonly gatewayPixSimulado = computed(() => this._config()?.gatewayPixSimulado ?? false);

  carregar(): void {
    this.http.get<ConfigPagamentoPublica>('/api/v1/pagamentos/config').subscribe({
      next: (config) => this._config.set(config),
      // Backend indisponivel ou feature desligada: comporta-se como tudo desativado.
      error: () => this._config.set(null),
    });
  }
}
