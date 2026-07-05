import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

export type ModoPagamentoMesa = 'PRE_PAGO' | 'POS_PAGO';

export interface ConfiguracaoPagamento {
  pixTotemAtivo: boolean;
  cartaoTotemAtivo: boolean;
  pixMesaAtivo: boolean;
  cartaoMesaAtivo: boolean;
  modoMesa: ModoPagamentoMesa;
}

@Component({
  selector: 'app-config-pagamento',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './config-pagamento.component.html',
  styleUrls: ['./config-pagamento.component.css'],
})
export class ConfigPagamentoComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/admin/pagamentos/config';

  readonly carregando = signal(true);
  readonly salvando = signal(false);
  readonly mensagem = signal<string | null>(null);
  readonly erro = signal<string | null>(null);

  config: ConfiguracaoPagamento = {
    pixTotemAtivo: false,
    cartaoTotemAtivo: false,
    pixMesaAtivo: false,
    cartaoMesaAtivo: false,
    modoMesa: 'PRE_PAGO',
  };

  ngOnInit(): void {
    this.http.get<ConfiguracaoPagamento>(this.apiUrl).subscribe({
      next: (config) => {
        this.config = config;
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set('Não foi possível carregar a configuração de pagamentos.');
        this.carregando.set(false);
      },
    });
  }

  salvar(): void {
    this.salvando.set(true);
    this.mensagem.set(null);
    this.erro.set(null);
    this.http.put<ConfiguracaoPagamento>(this.apiUrl, this.config).subscribe({
      next: (config) => {
        this.config = config;
        this.salvando.set(false);
        this.mensagem.set('Configuração de pagamentos salva.');
      },
      error: () => {
        this.salvando.set(false);
        this.erro.set('Erro ao salvar a configuração de pagamentos.');
      },
    });
  }
}
