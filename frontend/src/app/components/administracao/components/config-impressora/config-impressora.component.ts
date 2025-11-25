import { Component, inject, signal, ChangeDetectionStrategy, OnInit, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ImpressaoService, TipoImpressora } from '../../../../services/impressao.service';
import { AuthService } from '../../../../services/auth.service';

@Component({
  selector: 'app-config-impressora',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './config-impressora.component.html',
  styleUrl: './config-impressora.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConfigImpressoraComponent implements OnInit {
  private readonly impressaoService = inject(ImpressaoService);
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  
  readonly isAdministrador = computed(() => this.authService.isAdministrador());

  readonly estaExpandido = signal(false);
  readonly estaImprimindo = signal(false);
  readonly estaSalvando = signal(false);
  readonly estaCarregando = signal(false);
  readonly mensagemImpressao = signal<string | null>(null);
  
  readonly formImpressora: FormGroup;
  
  readonly tiposImpressora = [
    { value: TipoImpressora.EPSON_TM_T20, label: 'EPSON TM-T20' },
    { value: TipoImpressora.DARUMA_800, label: 'DARUMA DR-800' },
    { value: TipoImpressora.GENERICA_ESCPOS, label: 'Genérica ESC/POS' }
  ];

  constructor() {
    this.formImpressora = this.fb.group({
      tipoImpressora: [TipoImpressora.EPSON_TM_T20, [Validators.required]],
      nomeEstabelecimento: ['experimenta-ai-do-soneca', [Validators.required]],
      enderecoEstabelecimento: [''],
      telefoneEstabelecimento: [''],
      cnpjEstabelecimento: ['']
    });
  }

  ngOnInit(): void {
    this.carregarConfiguracao();
  }

  carregarConfiguracao(): void {
    this.estaCarregando.set(true);
    this.impressaoService.buscarConfiguracao().subscribe({
      next: (config) => {
        if (config) {
          this.formImpressora.patchValue({
            tipoImpressora: config.tipoImpressora,
            nomeEstabelecimento: config.nomeEstabelecimento,
            enderecoEstabelecimento: config.enderecoEstabelecimento || '',
            telefoneEstabelecimento: config.telefoneEstabelecimento || '',
            cnpjEstabelecimento: config.cnpjEstabelecimento || ''
          });
        }
        this.estaCarregando.set(false);
      },
      error: () => {
        this.estaCarregando.set(false);
      }
    });
  }

  alternarExpansao(): void {
    this.estaExpandido.update(v => !v);
    this.mensagemImpressao.set(null);
  }

  salvarConfiguracao(): void {
    if (this.formImpressora.invalid) {
      this.formImpressora.markAllAsTouched();
      return;
    }

    this.estaSalvando.set(true);
    this.mensagemImpressao.set(null);

    const config = this.formImpressora.value;
    
    this.impressaoService.salvarConfiguracao({
      tipoImpressora: config.tipoImpressora,
      nomeEstabelecimento: config.nomeEstabelecimento,
      enderecoEstabelecimento: config.enderecoEstabelecimento,
      telefoneEstabelecimento: config.telefoneEstabelecimento,
      cnpjEstabelecimento: config.cnpjEstabelecimento
    }).subscribe({
      next: () => {
        this.estaSalvando.set(false);
        this.mensagemImpressao.set('✅ Configuração salva com sucesso!');
        setTimeout(() => this.mensagemImpressao.set(null), 3000);
      },
      error: (error) => {
        this.estaSalvando.set(false);
        this.mensagemImpressao.set('❌ Erro ao salvar: ' + (error.error?.message || error.message || 'Erro desconhecido'));
        console.error('Erro ao salvar:', error);
      }
    });
  }

  testarImpressao(): void {
    if (this.formImpressora.invalid) {
      this.formImpressora.markAllAsTouched();
      return;
    }

    this.estaImprimindo.set(true);
    this.mensagemImpressao.set(null);

    const config = this.formImpressora.value;
    
    this.impressaoService.imprimirCupomTeste({
      tipoImpressora: config.tipoImpressora,
      nomeEstabelecimento: config.nomeEstabelecimento,
      enderecoEstabelecimento: config.enderecoEstabelecimento,
      telefoneEstabelecimento: config.telefoneEstabelecimento,
      cnpjEstabelecimento: config.cnpjEstabelecimento
    }).subscribe({
      next: (response) => {
        this.estaImprimindo.set(false);
        if (response.sucesso) {
          this.mensagemImpressao.set('✅ Cupom de teste impresso com sucesso!');
        } else {
          this.mensagemImpressao.set('❌ Erro: ' + response.mensagem);
        }
      },
      error: (error) => {
        this.estaImprimindo.set(false);
        this.mensagemImpressao.set('❌ Erro ao imprimir: ' + (error.error?.message || error.message || 'Erro desconhecido'));
        console.error('Erro ao imprimir:', error);
      }
    });
  }

  get tipoImpressora() {
    return this.formImpressora.get('tipoImpressora');
  }

  get nomeEstabelecimento() {
    return this.formImpressora.get('nomeEstabelecimento');
  }
}

