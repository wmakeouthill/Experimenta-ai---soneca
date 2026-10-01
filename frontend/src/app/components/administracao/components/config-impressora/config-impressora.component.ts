import { Component, inject, signal, ChangeDetectionStrategy, OnInit, computed, effect, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ImpressaoService, TipoImpressora } from '../../../../services/impressao.service';
import { AuthService } from '../../../../services/auth.service';
import { UploadUtil } from '../../../../utils/upload.util';
import { ImpressoraUtil } from '../../../../utils/impressora.util';
import { ElectronImpressoraService, ImpressoraSistema } from '../../../../services/electron-impressora.service';

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
  private readonly electronImpressoraService = inject(ElectronImpressoraService);
  
  readonly isAdministrador = computed(() => this.authService.isAdministrador());
  @ViewChild('logoInput') logoInput?: ElementRef<HTMLInputElement>;
  
  readonly estaExpandido = signal(false);
  readonly estaImprimindo = signal(false);
  readonly estaSalvando = signal(false);
  readonly estaCarregando = signal(false);
  readonly mensagemImpressao = signal<string | null>(null);
  readonly logoPreview = signal<string | null>(null);
  readonly mostrarAjudaDevicePath = signal(false);
  readonly erroDevicePath = signal<string | null>(null);
  readonly abaAjudaAtiva = signal<'windows' | 'linux' | 'rede'>('windows');
  readonly impressorasDisponiveis = signal<ImpressoraSistema[]>([]);
  readonly estaCarregandoImpressoras = signal(false);
  readonly estaNoElectron = computed(() => this.electronImpressoraService.estaRodandoNoElectron());
  readonly tamanhosPapel = [
    { value: 58, label: '58 mm (bobina estreita)' },
    { value: 80, label: '80 mm (bobina larga)' }
  ];
  readonly tamanhosFonte = [
    { value: 'PEQUENA', label: 'Pequena (mais itens por página)' },
    { value: 'NORMAL', label: 'Normal (equilíbrio)' },
    { value: 'GRANDE', label: 'Grande (mais legível)' }
  ];
  
  readonly formImpressora: FormGroup;
  
  readonly tiposImpressora = [
    { value: TipoImpressora.EPSON_TM_T20, label: 'EPSON TM-T20 / TM-T20X' },
    { value: TipoImpressora.DARUMA_800, label: 'DARUMA DR-800' },
    { value: TipoImpressora.DIEBOLD_IM693H, label: 'Diebold Nixdorf' },
    { value: TipoImpressora.GENERICA_ESCPOS, label: 'Genérica ESC/POS' }
  ];

  constructor() {
    // Preserva o padrão das instalações existentes (incluindo Diebold).
    const tipoPadrao = TipoImpressora.GENERICA_ESCPOS;
    
    this.formImpressora = this.fb.group({
      tipoImpressora: [tipoPadrao, [Validators.required]],
      devicePath: [''], // Ex: 127.0.0.1:9100 (rede), COM3 (Windows), /dev/usb/lp0 (Linux)
      larguraPapel: [80],
      tamanhoFonte: ['NORMAL'],
      nomeEstabelecimento: ['experimenta-ai-do-soneca', [Validators.required]],
      enderecoEstabelecimento: [''],
      telefoneEstabelecimento: [''],
      cnpjEstabelecimento: ['']
    });
    
    effect(() => {
      const isAdmin = this.isAdministrador();
      if (isAdmin) {
        this.formImpressora.enable();
      } else {
        this.formImpressora.disable();
      }
    });

    // Validação em tempo real do devicePath
    this.formImpressora.get('devicePath')?.valueChanges.subscribe(value => {
      if (value && value.trim().length > 0) {
        if (this.impressorasDisponiveis().some(p => p.tipo === 'windows' && p.name === value.trim())) {
          this.erroDevicePath.set(null);
          return;
        }
        const validacao = ImpressoraUtil.validarDevicePath(value);
        if (!validacao.valido) {
          this.erroDevicePath.set(validacao.erro || 'Formato inválido');
        } else {
          this.erroDevicePath.set(null);
        }
      } else {
        this.erroDevicePath.set(null);
      }
    });

  }

  readonly instrucoes = ImpressoraUtil.obterInstrucoesDevicePath();
  readonly soDetectado = computed(() => ImpressoraUtil.detectarSO());
  readonly placeholderSugerido = ImpressoraUtil.obterPlaceholderSugerido();


  carregarConfiguracao(): void {
    this.estaCarregando.set(true);
    this.impressaoService.buscarConfiguracao().subscribe({
      next: async (config) => {
        if (config) {
          const tipoParaUsar = config.tipoImpressora || TipoImpressora.GENERICA_ESCPOS;
          
          // Aplica valores no formulário
          this.formImpressora.patchValue({
            tipoImpressora: tipoParaUsar,
            devicePath: config.devicePath || '',
            larguraPapel: config.larguraPapel || 80,
            tamanhoFonte: config.tamanhoFonte || 'NORMAL',
            nomeEstabelecimento: config.nomeEstabelecimento,
            enderecoEstabelecimento: config.enderecoEstabelecimento || '',
            telefoneEstabelecimento: config.telefoneEstabelecimento || '',
            cnpjEstabelecimento: config.cnpjEstabelecimento || ''
          });
          
          // Se está no Electron e tem devicePath configurado
          if (this.estaNoElectron() && config.devicePath) {
            // Se as impressoras ainda não foram carregadas, carrega agora
            if (this.impressorasDisponiveis().length === 0) {
              await this.carregarImpressorasDisponiveis();
            }
            
            // Verifica se a impressora configurada está na lista
            const impressoraConfigurada = this.impressorasDisponiveis().find(
              p => p.name === config.devicePath || p.devicePath === config.devicePath
            );
            
            if (impressoraConfigurada) {
              // Configurações antigas por porta continuam válidas; novos salvamentos usam o nome.
              this.formImpressora.patchValue({ devicePath: this.caminhoImpressora(impressoraConfigurada) });
              // Mostra mensagem informando que a impressora foi carregada
              this.mensagemImpressao.set(`✅ Impressora "${impressoraConfigurada.name}" carregada da configuração salva.`);
              setTimeout(() => this.mensagemImpressao.set(null), 3000);
            }
          }
          
          if (config.logoBase64) {
            this.logoPreview.set(config.logoBase64);
          }
        } else {
          // Se não há configuração salva e está no Electron, seleciona impressora padrão
          if (this.estaNoElectron()) {
            await this.selecionarImpressoraPadraoSeNecessario();
          }
        }
        this.estaCarregando.set(false);
      },
      error: async () => {
        this.estaCarregando.set(false);
        // Em caso de erro, também tenta selecionar impressora padrão
        if (this.estaNoElectron()) {
          await this.selecionarImpressoraPadraoSeNecessario();
        }
      }
    });
  }
  
  async onLogoSelecionado(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    
    if (!file) {
      return;
    }
    
    if (!UploadUtil.eImagem(file)) {
      this.mensagemImpressao.set('❌ Por favor, selecione um arquivo de imagem válido');
      return;
    }
    
    try {
      const base64 = await UploadUtil.fileParaBase64(file);
      this.logoPreview.set(base64);
      this.mensagemImpressao.set(null);
    } catch (error) {
      this.mensagemImpressao.set('❌ Erro ao carregar imagem: ' + (error instanceof Error ? error.message : 'Erro desconhecido'));
    }
  }
  
  removerLogo(): void {
    this.logoPreview.set(null);
    if (this.logoInput) {
      this.logoInput.nativeElement.value = '';
    }
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

    // Valida se devicePath foi preenchido (obrigatório)
    if (!config.devicePath || config.devicePath.trim().length === 0) {
      this.mensagemImpressao.set('❌ Selecione uma impressora ou informe o caminho do dispositivo');
      this.estaSalvando.set(false);
      return;
    }
    
    this.impressaoService.salvarConfiguracao({
      tipoImpressora: config.tipoImpressora,
      devicePath: config.devicePath?.trim() || undefined,
      larguraPapel: config.larguraPapel || 80,
      tamanhoFonte: config.tamanhoFonte || 'NORMAL',
      nomeEstabelecimento: config.nomeEstabelecimento,
      enderecoEstabelecimento: config.enderecoEstabelecimento,
      telefoneEstabelecimento: config.telefoneEstabelecimento,
      cnpjEstabelecimento: config.cnpjEstabelecimento,
      logoBase64: this.logoPreview() || undefined
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

    const config = this.formImpressora.value;
    
    // Valida se devicePath foi preenchido
    if (!config.devicePath || config.devicePath.trim().length === 0) {
      this.mensagemImpressao.set('❌ Selecione uma impressora antes de testar');
      return;
    }

    this.estaImprimindo.set(true);
    this.mensagemImpressao.set(null);

    this.impressaoService.imprimirCupomTeste({
      tipoImpressora: config.tipoImpressora,
      devicePath: config.devicePath, // Passa o devicePath configurado
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
        
        // Tratamento específico de erros de autenticação
        if (error.status === 401 || error.status === 403) {
          this.mensagemImpressao.set('❌ Erro de autenticação. Faça login novamente.');
          console.error('Erro de autenticação:', error);
          // O authErrorInterceptor já vai redirecionar para login
          return;
        }
        
        // Tratamento de erro 400 (validação ou outro erro)
        let mensagemErro = 'Erro desconhecido';
        if (error.error) {
          if (error.error.mensagem) {
            mensagemErro = error.error.mensagem;
          } else if (error.error.message) {
            mensagemErro = error.error.message;
          } else if (error.error.errors) {
            const erros = Object.values(error.error.errors).join(', ');
            mensagemErro = `Erro de validação: ${erros}`;
          }
        } else if (error.message) {
          mensagemErro = error.message;
        }
        
        this.mensagemImpressao.set('❌ Erro ao imprimir: ' + mensagemErro);
        console.error('Erro ao imprimir:', error);
        console.error('Detalhes do erro:', {
          status: error.status,
          statusText: error.statusText,
          error: error.error,
          headers: error.headers
        });
      }
    });
  }

  get tipoImpressora() {
    return this.formImpressora.get('tipoImpressora');
  }

  get nomeEstabelecimento() {
    return this.formImpressora.get('nomeEstabelecimento');
  }

  alternarAjudaDevicePath(): void {
    this.mostrarAjudaDevicePath.update(v => !v);
  }

  obterInstrucoesParaSO(): string[] {
    const aba = this.abaAjudaAtiva();
    if (aba === 'windows') {
      return this.instrucoes.windows;
    } else if (aba === 'linux') {
      return this.instrucoes.linux;
    } else {
      return this.instrucoes.rede;
    }
  }
  
  ngOnInit(): void {
    // Define a aba inicial baseada no SO detectado
    const so = ImpressoraUtil.detectarSO();
    if (so === 'windows') {
      this.abaAjudaAtiva.set('windows');
    } else if (so === 'linux') {
      this.abaAjudaAtiva.set('linux');
    } else {
      this.abaAjudaAtiva.set('windows'); // Padrão
    }
    
    // Se estiver rodando no Electron, carrega impressoras PRIMEIRO
    // Depois carrega configuração para que o select já esteja populado
    if (this.estaNoElectron()) {
      this.carregarImpressorasDisponiveis().then(() => {
        // Após carregar impressoras, carrega configuração
        // Assim o select já está populado quando o devicePath é aplicado
        this.carregarConfiguracao();
      });
    } else {
      // No Web, apenas carrega configuração
      this.carregarConfiguracao();
    }
  }

  /**
   * Seleciona automaticamente a impressora padrão se não houver configuração salva
   */
  async selecionarImpressoraPadraoSeNecessario(): Promise<void> {
    // Só funciona no Electron
    if (!this.estaNoElectron()) {
      return;
    }

    // Verifica se já tem devicePath configurado
    const devicePathAtual = this.formImpressora.get('devicePath')?.value;
    if (devicePathAtual && devicePathAtual.trim().length > 0) {
      return; // Já tem impressora configurada
    }

    try {
      // Obtém impressora padrão
      const padrao = await this.electronImpressoraService.obterImpressoraPadrao();
      if (padrao) {
        // Seleciona automaticamente a impressora padrão
        this.selecionarImpressora(padrao);
        console.log('✅ Impressora padrão selecionada automaticamente:', padrao.name);
      }
    } catch (error) {
      console.warn('⚠️ Não foi possível selecionar impressora padrão:', error);
    }
  }

  async carregarImpressorasDisponiveis(): Promise<void> {
    if (!this.estaNoElectron()) {
      return;
    }

    this.estaCarregandoImpressoras.set(true);
    this.impressorasDisponiveis.set([]); // Limpa lista anterior
    this.mensagemImpressao.set(null);
    
    try {
      const impressoras = await this.electronImpressoraService.listarImpressoras();
      
      if (impressoras.length === 0) {
        this.mensagemImpressao.set('⚠️ Nenhuma impressora detectada. Verifique se há impressoras instaladas no sistema.');
        this.impressorasDisponiveis.set([]);
      } else {
        this.impressorasDisponiveis.set(impressoras);
        
        // Verifica se precisa selecionar impressora padrão automaticamente
        const devicePathAtual = this.formImpressora.get('devicePath')?.value;
        if (!devicePathAtual || devicePathAtual.trim().length === 0) {
          // Seleciona automaticamente a impressora padrão
          await this.selecionarImpressoraPadraoSeNecessario();
        } else {
          // Já tem impressora configurada, apenas mostra mensagem
          this.mensagemImpressao.set(`✅ ${impressoras.length} impressora(s) detectada(s).`);
        }
      }
      
      // Remove mensagem após 5 segundos
      setTimeout(() => {
        if (this.mensagemImpressao()?.includes('✅') || this.mensagemImpressao()?.includes('💡')) {
          this.mensagemImpressao.set(null);
        }
      }, 5000);
    } catch (error) {
      console.error('Erro ao carregar impressoras:', error);
      this.mensagemImpressao.set('❌ Erro ao detectar impressoras: ' + (error instanceof Error ? error.message : 'Erro desconhecido'));
      this.impressorasDisponiveis.set([]);
    } finally {
      this.estaCarregandoImpressoras.set(false);
    }
  }

  onImpressoraSelecionada(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const devicePath = select.value;
    if (devicePath) {
      const impressora = this.impressorasDisponiveis().find(p => this.caminhoImpressora(p) === devicePath);
      if (impressora) {
        this.selecionarImpressora(impressora);
      }
    }
  }

  selecionarImpressora(impressora: ImpressoraSistema): void {
    this.formImpressora.patchValue({ devicePath: this.caminhoImpressora(impressora) });
    
    const mensagem = impressora.padrao 
      ? `✅ Impressora padrão "${impressora.name}" selecionada! (${impressora.devicePath})`
      : `✅ Impressora "${impressora.name}" selecionada! (${impressora.devicePath})`;
    this.mensagemImpressao.set(mensagem);
    this.erroDevicePath.set(null); // Limpa erros se houver
    setTimeout(() => {
      if (this.mensagemImpressao() === mensagem) {
        this.mensagemImpressao.set(null);
      }
    }, 4000);
  }

  caminhoImpressora(impressora: ImpressoraSistema): string {
    return impressora.tipo === 'windows' ? impressora.name : impressora.devicePath;
  }
}

