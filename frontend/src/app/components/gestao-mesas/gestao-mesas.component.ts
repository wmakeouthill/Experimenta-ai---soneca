import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Mesa } from '../../services/mesa.service';
import { useMesas } from './composables/use-mesas';
import { CartaoMesa, imprimirCartoesMesa, qrDataUrl } from './utils/cartao-mesa.util';

@Component({
  selector: 'app-gestao-mesas',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule],
  templateUrl: './gestao-mesas.component.html',
  styleUrl: './gestao-mesas.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GestaoMesasComponent implements OnInit {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);
  private readonly fb = inject(FormBuilder);

  readonly mesasComposable = useMesas();

  readonly mesas = this.mesasComposable.mesas;
  readonly carregando = this.mesasComposable.carregando;
  readonly erro = this.mesasComposable.erro;
  readonly totalMesas = this.mesasComposable.totalMesas;

  readonly mostrarModal = signal(false);
  readonly mostrarModalQrCode = signal(false);
  readonly mesaEditando = signal<Mesa | null>(null);
  readonly mesaQrCode = signal<Mesa | null>(null);
  readonly linkTotemCopiado = signal(false);
  readonly linkMesaCopiado = signal(false);

  readonly mesasAtivas = computed(() =>
    this.mesas()
      .filter(mesa => mesa.ativa)
      .sort((a, b) => a.numero - b.numero)
  );

  readonly qrCodeModal = computed(() => {
    const mesa = this.mesaQrCode();
    return mesa ? qrDataUrl(this.obterUrlQrCode(mesa)) : '';
  });

  readonly form: FormGroup;

  // URL do Auto-Atendimento (Totem)
  get urlAutoAtendimento(): string {
    if (!this.isBrowser) return '';
    const baseUrl = window.location.origin;
    return `${baseUrl}/autoatendimento`;
  }

  constructor() {
    this.form = this.fb.group({
      numero: [null, [Validators.required, Validators.min(1)]],
      nome: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      piso: ['TERREO'],
    });
  }

  ngOnInit(): void {
    if (this.isBrowser) {
      this.mesasComposable.carregarMesas();
    }
  }

  abrirModalCriar(): void {
    this.mesaEditando.set(null);
    this.form.reset({
      numero: this.sugerirProximoNumero(),
      nome: '',
      piso: 'TERREO',
    });
    this.mostrarModal.set(true);
  }

  abrirModalEditar(mesa: Mesa): void {
    this.mesaEditando.set(mesa);
    this.form.patchValue({
      numero: mesa.numero,
      nome: mesa.nome,
      piso: mesa.piso,
    });
    this.mostrarModal.set(true);
  }

  fecharModal(): void {
    this.mostrarModal.set(false);
    this.mesaEditando.set(null);
    this.form.reset();
  }

  async salvar(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const formValue = this.form.value;
    const mesaEditando = this.mesaEditando();

    if (mesaEditando) {
      const sucesso = await this.mesasComposable.atualizarMesa(mesaEditando.id, {
        numero: formValue.numero,
        nome: formValue.nome,
        piso: formValue.piso,
      });
      if (sucesso) {
        this.fecharModal();
      }
    } else {
      const sucesso = await this.mesasComposable.criarMesa({
        numero: formValue.numero,
        nome: formValue.nome,
        piso: formValue.piso,
      });
      if (sucesso) {
        this.fecharModal();
      }
    }
  }

  async excluirMesa(mesa: Mesa): Promise<void> {
    if (!confirm(`Tem certeza que deseja excluir a mesa ${mesa.numero} - ${mesa.nome}?`)) {
      return;
    }
    await this.mesasComposable.excluirMesa(mesa.id);
  }

  async alternarStatus(mesa: Mesa): Promise<void> {
    await this.mesasComposable.alternarStatus(mesa);
  }

  abrirQrCode(mesa: Mesa): void {
    this.mesaQrCode.set(mesa);
    this.mostrarModalQrCode.set(true);
  }

  fecharModalQrCode(): void {
    this.mostrarModalQrCode.set(false);
    this.mesaQrCode.set(null);
  }

  obterUrlQrCode(mesa: Mesa): string {
    return this.mesasComposable.gerarUrlQrCode(mesa);
  }

  copiarUrl(mesa: Mesa): void {
    if (!this.isBrowser) return;
    const url = this.obterUrlQrCode(mesa);
    this.copiarParaClipboard(url).then(sucesso => {
      if (sucesso) {
        this.linkMesaCopiado.set(true);
        setTimeout(() => this.linkMesaCopiado.set(false), 2000);
      }
    });
  }

  imprimirQrCode(mesa: Mesa): void {
    imprimirCartoesMesa([this.cartaoDaMesa(mesa)]);
  }

  imprimirTodas(): void {
    imprimirCartoesMesa(this.mesasAtivas().map(mesa => this.cartaoDaMesa(mesa)));
  }

  private cartaoDaMesa(mesa: Mesa): CartaoMesa {
    return { numero: mesa.numero, nome: mesa.nome, url: this.obterUrlQrCode(mesa) };
  }

  // ========== Totem Auto-Atendimento ==========
  copiarUrlAutoAtendimento(): void {
    if (!this.isBrowser) return;
    const url = this.urlAutoAtendimento;
    this.copiarParaClipboard(url).then(sucesso => {
      if (sucesso) {
        this.linkTotemCopiado.set(true);
        setTimeout(() => this.linkTotemCopiado.set(false), 2000);
      }
    });
  }

  /**
   * Copia texto para a área de transferência com fallback para HTTP.
   * navigator.clipboard requer HTTPS; o fallback usa execCommand.
   */
  private async copiarParaClipboard(texto: string): Promise<boolean> {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(texto);
        return true;
      }
      // Fallback para HTTP (execCommand)
      const textarea = document.createElement('textarea');
      textarea.value = texto;
      textarea.style.position = 'fixed';
      textarea.style.left = '-9999px';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      const sucesso = document.execCommand('copy');
      document.body.removeChild(textarea);
      return sucesso;
    } catch (err) {
      console.error('Erro ao copiar link:', err);
      return false;
    }
  }

  private sugerirProximoNumero(): number {
    const mesasAtuais = this.mesas();
    if (mesasAtuais.length === 0) return 1;
    const maxNumero = Math.max(...mesasAtuais.map(m => m.numero));
    return maxNumero + 1;
  }

  isEditando(): boolean {
    return this.mesaEditando() !== null;
  }

  get numero() {
    return this.form.get('numero');
  }
  get nome() {
    return this.form.get('nome');
  }
}
