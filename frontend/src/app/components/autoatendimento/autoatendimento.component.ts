import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  OnDestroy,
  OnInit,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom, Subject, takeUntil } from 'rxjs';

import { AdicionalService } from '../../services/adicional.service';
import { AuthService } from '../../services/auth.service';
import {
  AutoAtendimentoService,
  CriarPedidoAutoAtendimentoRequest,
  ItemPedidoAutoAtendimentoRequest,
  MeioPagamentoAutoAtendimentoRequest,
  PedidoAutoAtendimentoResponse,
} from '../../services/autoatendimento.service';
import {
  type MeioPagamentoGateway,
  type PagamentoDTO,
  PagamentoService,
  type PixCobrancaCriadaDTO,
} from '../../services/pagamento.service';
import { PagamentoConfigService } from '../../services/pagamento-config.service';
import { Produto } from '../../services/produto.service';
import { StatusLoja, StatusLojaService } from '../../services/status-loja.service';
import { ImageProxyUtil } from '../../utils/image-proxy.util';

import { AbaNavegacaoAutoatendimento, AutoatendimentoFooterNavComponent } from './components';
import {
  type ItemCarrinhoTotem,
  useAutoAtendimentoCardapio,
  useAutoAtendimentoCarrinho,
  useAutoAtendimentoCliente,
  useAutoAtendimentoInicio,
  useAutoAtendimentoPagamento,
} from './composables';

type EtapaTotem = 'cardapio' | 'pagamento' | 'confirmacao' | 'sucesso';
type MeioPagamentoTipo = 'PIX' | 'CARTAO_CREDITO' | 'CARTAO_DEBITO' | 'VALE_REFEICAO' | 'DINHEIRO';
type StatusCheckoutTotem = 'PIX_QR' | 'CARTAO_PROCESSANDO' | 'ERRO';

interface PagamentoCheckoutTotem {
  status: StatusCheckoutTotem;
  correlationId: string;
  pedido: PedidoAutoAtendimentoResponse;
  pix?: PixCobrancaCriadaDTO;
  mensagem?: string;
}

interface ResultadoTefTotem {
  sucesso: boolean;
  status: string;
  correlationId: string;
  nsu?: string;
  bandeira?: string;
  autorizacao?: string;
  adquirente?: string;
  mensagem?: string;
}

interface TotemApi {
  iniciarPagamentoTef?: (payload: {
    correlationId: string;
    valorCentavos: number;
    meio: MeioPagamentoGateway;
  }) => Promise<ResultadoTefTotem>;
}

/**
 * Componente de auto atendimento para totem.
 * Baseado no pedido-cliente-mesa, mas com autenticação de operador.
 * Permite criar pedidos contínuos sem identificação obrigatória do cliente.
 */
@Component({
  selector: 'app-autoatendimento',
  standalone: true,
  imports: [CommonModule, FormsModule, AutoatendimentoFooterNavComponent],
  templateUrl: './autoatendimento.component.html',
  styleUrls: [
    './styles/base.css',
    './styles/cardapio.css',
    './styles/modal.css',
    './styles/carrinho.css',
    './styles/pagamento.css',
    './styles/abas.css',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AutoatendimentoComponent implements OnInit, OnDestroy {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly adicionalService = inject(AdicionalService);
  private readonly autoAtendimentoService = inject(AutoAtendimentoService);
  private readonly pagamentoService = inject(PagamentoService);
  private readonly statusLojaService = inject(StatusLojaService);
  readonly pagamentoConfig = inject(PagamentoConfigService);
  private readonly destroy$ = new Subject<void>();

  protected readonly Math = Math;

  // ========== Status da Loja ==========
  readonly statusLoja = signal<StatusLoja>(StatusLoja.FECHADA);
  readonly verificandoStatusLoja = signal(true);
  readonly mensagemStatusLoja = signal<string | null>(null);

  // ========== Estado Geral ==========
  readonly carregando = signal(true);
  readonly erro = signal<string | null>(null);
  readonly etapaAtual = signal<EtapaTotem | null>(null);
  readonly abaAtual = signal<AbaNavegacaoAutoatendimento>('inicio');
  readonly enviando = signal(false);
  readonly checkoutPagamento = signal<PagamentoCheckoutTotem | null>(null);
  readonly erroPagamento = signal<string | null>(null);

  // ========== Countdown do QR Code PIX ==========
  readonly pixTempoRestante = signal(0);
  readonly pixExpirado = computed(() => this.pixTempoRestante() <= 0);
  private pixTimer?: ReturnType<typeof setInterval>;

  // Estado do pedido criado
  /** Quando naFila: pedido na fila aguardando aceite; senão: pedido já aceito (tem numeroPedido). */
  readonly pedidoCriado = signal<{
    id: string;
    numeroPedido?: number | string;
    mensagem?: string;
    naFila?: boolean;
  } | null>(null);

  // Timer de inatividade (em segundos)
  private inactivityTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly INACTIVITY_TIMEOUT = 90; // 1 minuto e meio
  readonly tempoInatividade = signal(0);
  readonly mostrarAvisoInatividade = signal(false);

  // ========== Composables ==========
  readonly cardapio = useAutoAtendimentoCardapio();
  readonly carrinho = useAutoAtendimentoCarrinho();
  readonly pagamento = useAutoAtendimentoPagamento(() => this.carrinho.totalValor());
  readonly cliente = useAutoAtendimentoCliente();
  readonly inicio = useAutoAtendimentoInicio();

  // ========== Input para nome do cliente ==========
  nomeClienteInput = '';

  // ========== Computed ==========
  readonly operadorLogado = computed(() => this.authService.usuarioAtual());

  readonly podeEnviarPedido = computed(
    () => this.carrinho.podeEnviarPedido() && this.pagamento.pagamentoValido() && !this.enviando()
  );

  readonly meioPagamentoIntegradoSelecionado = computed(() => {
    const meios = this.pagamento.meiosSelecionados();
    if (this.pagamento.dividido() || meios.length !== 1) {
      return null;
    }
    const meio = meios[0];
    const meioGateway = meio ? this.mapearMeioPagamentoTotem(meio.tipo as MeioPagamentoTipo) : null;
    if (!meioGateway) {
      return null;
    }
    // Respeita a configuracao efetiva de pagamentos do totem: sem o flag correspondente
    // ativo, o meio selecionado nao entra no fluxo integrado (segue como meio manual).
    if (meioGateway === 'PIX') {
      return this.pagamentoConfig.pixTotemAtivo() ? meioGateway : null;
    }
    return this.pagamentoConfig.cartaoTotemAtivo() ? meioGateway : null;
  });

  constructor() {
    // Effect para carregar adicionais quando abre detalhes de produto
    effect(() => {
      const produto = this.carrinho.produtoSelecionado();
      if (produto && this.isBrowser && this.carrinho.mostrarDetalhes()) {
        // Usa setTimeout para evitar erro de signal write dentro de effect
        setTimeout(() => this.carregarAdicionaisProduto(produto.id), 0);
      }
    });
  }

  ngOnInit(): void {
    this.pagamentoConfig.carregar();

    if (!this.isBrowser) return;

    // Verifica se operador está logado
    if (!this.authService.estaAutenticado()) {
      this.router.navigate(['/login'], {
        queryParams: { returnUrl: '/autoatendimento' },
      });
      return;
    }

    // Verifica status da loja
    this.verificarStatusLoja();
    this.conectarStatusLojaSSE();

    this.carregarCardapio();
    this.carregarInicio();
    this.iniciarMonitoramentoInatividade();
  }

  ngOnDestroy(): void {
    this.pararMonitoramentoInatividade();
    this.pararContagemPix();
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ========== Status da Loja ==========
  verificarStatusLoja(): void {
    this.verificandoStatusLoja.set(true);
    this.statusLojaService.verificarStatus().subscribe({
      next: response => {
        this.statusLoja.set(response.status);
        this.mensagemStatusLoja.set(response.mensagem);
        this.verificandoStatusLoja.set(false);
      },
      error: () => {
        this.statusLoja.set(StatusLoja.FECHADA);
        this.mensagemStatusLoja.set('Não foi possível verificar o status da loja.');
        this.verificandoStatusLoja.set(false);
      },
    });
  }

  private conectarStatusLojaSSE(): void {
    this.statusLojaService
      .conectarStream()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: response => {
          this.statusLoja.set(response.status);
          this.mensagemStatusLoja.set(response.mensagem);
          if (this.verificandoStatusLoja()) {
            this.verificandoStatusLoja.set(false);
          }
        },
      });
  }

  // ========== Carregamento ==========
  private async carregarCardapio(): Promise<void> {
    this.carregando.set(true);
    this.erro.set(null);

    try {
      await this.cardapio.carregar();
    } catch (e) {
      this.erro.set('Erro ao carregar cardápio. Tente novamente.');
      console.error('Erro ao carregar cardápio:', e);
    } finally {
      this.carregando.set(false);
    }
  }

  private async carregarInicio(): Promise<void> {
    try {
      await this.inicio.carregar();
    } catch (e) {
      console.error('Erro ao carregar produtos populares:', e);
      // Não bloqueia a tela se falhar
    }
  }

  private carregarAdicionaisProduto(produtoId: string): void {
    this.carrinho.setCarregandoAdicionais(true);
    this.adicionalService.listarAdicionaisDoProduto(produtoId).subscribe({
      next: adicionais => {
        // Filtra apenas os disponíveis
        const disponiveis = adicionais.filter(a => a.disponivel);
        this.carrinho.setAdicionaisDisponiveis(disponiveis);
        this.carrinho.setCarregandoAdicionais(false);
      },
      error: err => {
        console.error('Erro ao carregar adicionais:', err);
        this.carrinho.setAdicionaisDisponiveis([]);
        this.carrinho.setCarregandoAdicionais(false);
      },
    });
  }

  // ========== Navegação ==========
  navegarPara(aba: AbaNavegacaoAutoatendimento): void {
    this.abaAtual.set(aba);
    this.etapaAtual.set(null);
    this.limparCheckoutPagamento();
    this.resetarInatividade();
  }

  irParaCardapio(): void {
    this.navegarPara('cardapio');
  }

  irParaCarrinho(): void {
    this.navegarPara('carrinho');
  }

  irParaPagamento(): void {
    this.limparCheckoutPagamento();
    this.etapaAtual.set('pagamento');
    this.resetarInatividade();
  }

  irParaConfirmacao(): void {
    if (this.pagamento.pagamentoValido()) {
      this.etapaAtual.set('confirmacao');
      this.resetarInatividade();
    }
  }

  voltarEtapa(): void {
    const etapa = this.etapaAtual();
    if (etapa) {
      switch (etapa) {
        case 'pagamento':
          this.limparCheckoutPagamento();
          this.etapaAtual.set(null);
          this.abaAtual.set('carrinho');
          break;
        case 'confirmacao':
          this.etapaAtual.set('pagamento');
          break;
      }
    } else {
      // Se está em uma aba, volta para início
      this.navegarPara('inicio');
    }
    this.resetarInatividade();
  }

  // ========== Cardápio ==========
  abrirDetalhesProduto(produto: Produto): void {
    this.carrinho.abrirDetalhes(produto);
    this.resetarInatividade();
  }

  adicionarAoCarrinhoRapido(produto: Produto): void {
    // Adiciona direto ao carrinho sem abrir modal
    this.carrinho.abrirDetalhes(produto);
    this.carrinho.confirmarProduto();
    this.resetarInatividade();
  }

  // ========== Pedido ==========
  async finalizarPedido(): Promise<void> {
    if (!this.podeEnviarPedido()) return;

    this.enviando.set(true);
    this.resetarInatividade();

    // Gera chave de idempotência UMA VEZ por tentativa de envio.
    // Se houver double-click, o guard `podeEnviarPedido` (que verifica !enviando()) impede nova execução.
    const idempotencyKey = this.autoAtendimentoService.gerarChaveIdempotencia();

    try {
      const request = this.montarRequestPedido();
      const response = await firstValueFrom(
        this.autoAtendimentoService.criarPedido(request, idempotencyKey)
      );

      const naFila = (response as { status?: string }).status === 'NA_FILA';
      this.pedidoCriado.set({
        id: response.id,
        ...(naFila
          ? {
              mensagem:
                (response as { mensagem?: string }).mensagem ??
                'Aguarde a confirmação do atendente.',
              naFila: true,
            }
          : { numeroPedido: (response as unknown as { numeroPedido: number }).numeroPedido }),
      });

      this.etapaAtual.set('sucesso');

      // Auto-reset após 10 segundos
      setTimeout(() => {
        this.novoAtendimento();
      }, 10000);
    } catch (e) {
      console.error('Erro ao criar pedido:', e);
      this.erro.set('Erro ao criar pedido. Tente novamente.');
    } finally {
      this.enviando.set(false);
    }
  }

  private montarRequestPedido(incluirMeiosPagamento = true): CriarPedidoAutoAtendimentoRequest {
    const itens: ItemPedidoAutoAtendimentoRequest[] = this.carrinho.itens().map(item => ({
      produtoId: item.produto.id,
      quantidade: item.quantidade,
      observacoes: item.observacao || undefined,
      adicionais: item.adicionais?.map(ad => ({
        adicionalId: ad.adicional.id,
        quantidade: ad.quantidade,
      })),
    }));

    const meiosPagamento: MeioPagamentoAutoAtendimentoRequest[] = incluirMeiosPagamento
      ? this.pagamento.getMeiosComTroco().map(m => ({
          meioPagamento: m.tipo as MeioPagamentoAutoAtendimentoRequest['meioPagamento'],
          valor: m.valor,
          valorPagoDinheiro: m.valorPagoDinheiro,
        }))
      : [];

    return {
      nomeCliente: this.nomeClienteInput.trim() || undefined,
      itens,
      meiosPagamento,
    };
  }

  novoAtendimento(): void {
    // Limpa tudo e volta para tela inicial
    this.carrinho.limparCarrinho();
    this.pagamento.resetar();
    this.cliente.limpar();
    this.nomeClienteInput = '';
    this.pedidoCriado.set(null);
    this.erro.set(null);
    this.limparCheckoutPagamento();
    this.etapaAtual.set(null);
    this.abaAtual.set('inicio');
    this.resetarInatividade();
  }

  cancelarPedido(): void {
    if (confirm('Deseja cancelar o pedido atual?')) {
      this.novoAtendimento();
    }
  }

  // ========== Inatividade ==========
  private iniciarMonitoramentoInatividade(): void {
    if (!this.isBrowser) return;

    document.addEventListener('touchstart', this.resetarInatividade.bind(this));
    document.addEventListener('click', this.resetarInatividade.bind(this));
    document.addEventListener('keypress', this.resetarInatividade.bind(this));

    this.resetarInatividade();
  }

  private pararMonitoramentoInatividade(): void {
    if (!this.isBrowser) return;

    document.removeEventListener('touchstart', this.resetarInatividade.bind(this));
    document.removeEventListener('click', this.resetarInatividade.bind(this));
    document.removeEventListener('keypress', this.resetarInatividade.bind(this));

    if (this.inactivityTimer) {
      clearTimeout(this.inactivityTimer);
    }
  }

  private resetarInatividade(): void {
    this.tempoInatividade.set(0);
    this.mostrarAvisoInatividade.set(false);

    if (this.inactivityTimer) {
      clearTimeout(this.inactivityTimer);
    }

    // Se estiver na aba início, reinicia automaticamente após timeout
    if (this.abaAtual() === 'inicio') {
      this.inactivityTimer = setTimeout(() => {
        this.novoAtendimento();
      }, this.INACTIVITY_TIMEOUT * 1000);
    } else if (this.etapaAtual() !== 'sucesso') {
      // Em outras telas, mostra aviso primeiro
      this.inactivityTimer = setTimeout(() => {
        this.mostrarAvisoInatividade.set(true);

        // Dá mais 30 segundos antes de resetar
        setTimeout(() => {
          if (this.mostrarAvisoInatividade()) {
            this.novoAtendimento();
          }
        }, 30000);
      }, this.INACTIVITY_TIMEOUT * 1000);
    }
  }

  continuarAtendimento(): void {
    this.mostrarAvisoInatividade.set(false);
    this.resetarInatividade();
  }

  // ========== Formatação ==========
  formatarPreco(valor: number): string {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    }).format(valor);
  }

  calcularSubtotalItem(item: ItemCarrinhoTotem): number {
    const subtotalAdicionais = item.adicionais.reduce(
      (total, adicional) => total + adicional.adicional.preco * adicional.quantidade,
      0
    );
    return (item.produto.preco + subtotalAdicionais) * item.quantidade;
  }

  getImagemProduto(produto: Produto): string {
    if (produto.foto) {
      return ImageProxyUtil.getProxyUrl(produto.foto) || produto.foto;
    }
    return '';
  }

  // ========== Pagamento ==========
  selecionarMeioPagamento(tipo: MeioPagamentoTipo): void {
    if (this.checkoutPagamento()) {
      return;
    }
    this.erroPagamento.set(null);
    this.pagamento.selecionarMeio(tipo);
    this.resetarInatividade();
  }

  async processarPagamento(): Promise<void> {
    if (!this.pagamento.pagamentoValido() || this.enviando()) {
      return;
    }

    const meioIntegrado = this.meioPagamentoIntegradoSelecionado();
    if (!meioIntegrado) {
      this.irParaConfirmacao();
      return;
    }

    this.enviando.set(true);
    this.erroPagamento.set(null);
    this.resetarInatividade();

    try {
      const correlationId = this.gerarCorrelationId();
      const pedido = await this.criarPedidoDiretoParaPagamento(correlationId);

      if (meioIntegrado === 'PIX') {
        await this.iniciarPagamentoPix(pedido, correlationId);
        return;
      }

      await this.iniciarPagamentoCartao(pedido, meioIntegrado, correlationId);
    } catch (error) {
      console.error('Erro ao processar pagamento do totem:', error);
      this.erroPagamento.set(this.getMensagemErroPagamento(error));
    } finally {
      this.enviando.set(false);
    }
  }

  async simularPixAprovado(): Promise<void> {
    const checkout = this.checkoutPagamento();
    if (!checkout?.pix || this.enviando() || this.pixExpirado()) {
      return;
    }

    this.enviando.set(true);
    this.erroPagamento.set(null);

    try {
      await firstValueFrom(this.pagamentoService.simularPixAprovado(checkout.pix.txid));
      const pagamento = await firstValueFrom(
        this.pagamentoService.buscarStatus(checkout.correlationId)
      );
      this.aplicarStatusPagamento(pagamento, checkout.pedido);
    } catch (error) {
      console.error('Erro ao simular PIX aprovado:', error);
      this.erroPagamento.set(this.getMensagemErroPagamento(error));
    } finally {
      this.enviando.set(false);
    }
  }

  async verificarPagamentoPix(): Promise<void> {
    const checkout = this.checkoutPagamento();
    if (!checkout || this.enviando() || this.pixExpirado()) {
      return;
    }

    this.enviando.set(true);
    this.erroPagamento.set(null);

    try {
      const pagamento = await firstValueFrom(
        this.pagamentoService.buscarStatus(checkout.correlationId)
      );
      this.aplicarStatusPagamento(pagamento, checkout.pedido);
    } catch (error) {
      console.error('Erro ao verificar pagamento PIX:', error);
      this.erroPagamento.set(this.getMensagemErroPagamento(error));
    } finally {
      this.enviando.set(false);
    }
  }

  async copiarCodigoPix(): Promise<void> {
    const pix = this.checkoutPagamento()?.pix;
    const codigo = pix?.copiaECola || pix?.qrCodePayload;
    if (!codigo || !this.isBrowser || !navigator.clipboard) {
      return;
    }

    try {
      await navigator.clipboard.writeText(codigo);
      this.erroPagamento.set('Codigo PIX copiado.');
    } catch {
      this.erroPagamento.set('Nao foi possivel copiar automaticamente.');
    }
  }

  private async criarPedidoDiretoParaPagamento(
    correlationId: string
  ): Promise<PedidoAutoAtendimentoResponse> {
    const request = this.montarRequestPedido(false);
    return firstValueFrom(this.autoAtendimentoService.criarPedidoDireto(request, correlationId));
  }

  private async iniciarPagamentoPix(
    pedido: PedidoAutoAtendimentoResponse,
    correlationId: string
  ): Promise<void> {
    const pix = await firstValueFrom(
      this.pagamentoService.iniciarPix({
        pedidoId: pedido.id,
        correlationId,
      })
    );

    this.checkoutPagamento.set({
      status: 'PIX_QR',
      correlationId,
      pedido,
      pix,
      mensagem: 'Aguardando pagamento PIX.',
    });
    this.iniciarContagemPix(pix.expiracaoEm);
  }

  /**
   * Gera um novo QR Code PIX para o mesmo pedido, com um novo correlationId.
   * O backend expira a cobranca anterior ao receber a nova solicitacao.
   */
  regenerarQrCodePix(): void {
    const pedido = this.checkoutPagamento()?.pedido;
    if (!pedido) {
      return;
    }
    const novoCorrelationId = this.gerarCorrelationId();
    void this.iniciarPagamentoPix(pedido, novoCorrelationId);
  }

  private async iniciarPagamentoCartao(
    pedido: PedidoAutoAtendimentoResponse,
    meioPagamento: MeioPagamentoGateway,
    correlationId: string
  ): Promise<void> {
    const pagamento = await firstValueFrom(
      this.pagamentoService.iniciarCartao({
        pedidoId: pedido.id,
        meioPagamento,
        correlationId,
      })
    );

    this.checkoutPagamento.set({
      status: 'CARTAO_PROCESSANDO',
      correlationId,
      pedido,
      mensagem: 'Siga as instrucoes na maquininha.',
    });

    const resultadoTef = await this.executarTef(pagamento, meioPagamento);
    if (!resultadoTef.sucesso) {
      await firstValueFrom(
        this.pagamentoService.confirmarCartao({
          correlationId,
          aprovado: false,
          motivo: resultadoTef.mensagem || resultadoTef.status,
        })
      );
      throw new Error(resultadoTef.mensagem || 'Pagamento nao aprovado na maquininha.');
    }

    const confirmado = await firstValueFrom(
      this.pagamentoService.confirmarCartao({
        correlationId,
        aprovado: true,
        nsuTef: resultadoTef.nsu,
        bandeira: resultadoTef.bandeira,
        codigoAutorizacao: resultadoTef.autorizacao,
        codigoAdquirente: resultadoTef.adquirente,
        comprovanteCliente: resultadoTef.mensagem,
      })
    );

    this.aplicarStatusPagamento(confirmado, pedido);
  }

  private async executarTef(
    pagamento: PagamentoDTO,
    meioPagamento: MeioPagamentoGateway
  ): Promise<ResultadoTefTotem> {
    const totemApi = this.getTotemApi();
    if (totemApi?.iniciarPagamentoTef) {
      return totemApi.iniciarPagamentoTef({
        correlationId: pagamento.correlationId,
        valorCentavos: pagamento.valorCentavos,
        meio: meioPagamento,
      });
    }

    await this.aguardar(900);
    const sufixo = String(Date.now()).slice(-8);
    return {
      sucesso: true,
      status: 'APROVADO',
      correlationId: pagamento.correlationId,
      nsu: `WEB${sufixo}`,
      bandeira: 'MOCK',
      autorizacao: `AUT${sufixo.slice(-6)}`,
      adquirente: 'BROWSER_MOCK',
      mensagem: 'Pagamento aprovado pelo mock do navegador.',
    };
  }

  private aplicarStatusPagamento(
    pagamento: PagamentoDTO,
    pedido: PedidoAutoAtendimentoResponse
  ): void {
    if (pagamento.status === 'APROVADO') {
      this.concluirPedidoPago(pedido);
      return;
    }

    if (['NEGADO', 'CANCELADO', 'FALHA_TECNICA', 'EXPIRADO'].includes(pagamento.status)) {
      this.erroPagamento.set(pagamento.motivo || 'Pagamento nao aprovado.');
      return;
    }

    this.erroPagamento.set('Pagamento ainda pendente. Verifique novamente em alguns segundos.');
  }

  private concluirPedidoPago(pedido: PedidoAutoAtendimentoResponse): void {
    this.pedidoCriado.set({
      id: pedido.id,
      numeroPedido: pedido.numeroPedido,
    });
    this.limparCheckoutPagamento();
    this.etapaAtual.set('sucesso');

    setTimeout(() => {
      this.novoAtendimento();
    }, 10000);
  }

  private limparCheckoutPagamento(): void {
    this.checkoutPagamento.set(null);
    this.erroPagamento.set(null);
    this.pararContagemPix();
  }

  private iniciarContagemPix(expiracaoEm: string): void {
    this.pararContagemPix();
    if (!this.isBrowser) {
      return;
    }
    const expiraEmMs = new Date(expiracaoEm).getTime();
    const atualizar = () => {
      const restante = Math.max(0, Math.floor((expiraEmMs - Date.now()) / 1000));
      this.pixTempoRestante.set(restante);
      if (restante <= 0) {
        this.pararContagemPix();
      }
    };
    atualizar();
    this.pixTimer = setInterval(atualizar, 1000);
  }

  private pararContagemPix(): void {
    if (this.pixTimer) {
      clearInterval(this.pixTimer);
      this.pixTimer = undefined;
    }
  }

  formatarTempoPix(segundos: number): string {
    const min = Math.floor(segundos / 60);
    const seg = segundos % 60;
    return `${min}:${seg.toString().padStart(2, '0')}`;
  }

  private mapearMeioPagamentoTotem(tipo: MeioPagamentoTipo): MeioPagamentoGateway | null {
    switch (tipo) {
      case 'PIX':
        return 'PIX';
      case 'CARTAO_CREDITO':
        return 'CARTAO_CREDITO';
      case 'CARTAO_DEBITO':
        return 'CARTAO_DEBITO';
      case 'VALE_REFEICAO':
        return 'CARTAO_VOUCHER';
      case 'DINHEIRO':
        return null;
    }
  }

  private gerarCorrelationId(): string {
    if (this.isBrowser && typeof window.crypto?.randomUUID === 'function') {
      return window.crypto.randomUUID();
    }
    return this.autoAtendimentoService.gerarChaveIdempotencia();
  }

  private getTotemApi(): TotemApi | null {
    if (!this.isBrowser) {
      return null;
    }
    return (window as Window & { totemAPI?: TotemApi }).totemAPI ?? null;
  }

  private aguardar(milliseconds: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, milliseconds));
  }

  private getMensagemErroPagamento(error: unknown): string {
    if (error instanceof Error) {
      return error.message;
    }

    const httpError = error as {
      error?: { message?: string; detail?: string; erro?: string };
      message?: string;
    };

    return (
      httpError.error?.message ||
      httpError.error?.detail ||
      httpError.error?.erro ||
      httpError.message ||
      'Nao foi possivel processar o pagamento. Tente novamente.'
    );
  }

  // ========== Debug/Admin ==========
  sairDoTotem(): void {
    if (confirm('Deseja sair do modo totem?')) {
      this.router.navigate(['/']);
    }
  }
}
