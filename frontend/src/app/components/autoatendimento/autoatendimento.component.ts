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
} from '../../services/autoatendimento.service';
import { Produto } from '../../services/produto.service';
import { StatusLoja, StatusLojaService } from '../../services/status-loja.service';
import { ImageProxyUtil } from '../../utils/image-proxy.util';

import { AbaNavegacaoAutoatendimento, AutoatendimentoFooterNavComponent } from './components';
import {
  useAutoAtendimentoCardapio,
  useAutoAtendimentoCarrinho,
  useAutoAtendimentoCliente,
  useAutoAtendimentoInicio,
  useAutoAtendimentoPagamento,
} from './composables';

type EtapaTotem = 'cardapio' | 'pagamento' | 'confirmacao' | 'sucesso';
type MeioPagamentoTipo = 'PIX' | 'CARTAO_CREDITO' | 'CARTAO_DEBITO' | 'VALE_REFEICAO' | 'DINHEIRO';

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
  private readonly statusLojaService = inject(StatusLojaService);
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

  // Estado do pedido criado
  readonly pedidoCriado = signal<{ id: string; numeroPedido: number } | null>(null);

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
    this.resetarInatividade();
  }

  irParaCardapio(): void {
    this.navegarPara('cardapio');
  }

  irParaCarrinho(): void {
    this.navegarPara('carrinho');
  }

  irParaPagamento(): void {
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

      this.pedidoCriado.set({
        id: response.id,
        numeroPedido: response.numeroPedido,
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

  private montarRequestPedido(): CriarPedidoAutoAtendimentoRequest {
    const itens: ItemPedidoAutoAtendimentoRequest[] = this.carrinho.itens().map(item => ({
      produtoId: item.produto.id,
      quantidade: item.quantidade,
      observacoes: item.observacao || undefined,
      adicionais: item.adicionais?.map(ad => ({
        adicionalId: ad.adicional.id,
        quantidade: ad.quantidade,
      })),
    }));

    const meiosPagamento: MeioPagamentoAutoAtendimentoRequest[] = this.pagamento
      .meiosSelecionados()
      .map((m: { tipo: string; valor: number }) => ({
        meioPagamento: m.tipo as MeioPagamentoAutoAtendimentoRequest['meioPagamento'],
        valor: m.valor,
      }));

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

  getImagemProduto(produto: Produto): string {
    if (produto.foto) {
      return ImageProxyUtil.getProxyUrl(produto.foto) || produto.foto;
    }
    return '';
  }

  // ========== Pagamento ==========
  selecionarMeioPagamento(tipo: MeioPagamentoTipo): void {
    this.pagamento.selecionarMeio(tipo);
    this.resetarInatividade();
  }

  // ========== Debug/Admin ==========
  sairDoTotem(): void {
    if (confirm('Deseja sair do modo totem?')) {
      this.router.navigate(['/']);
    }
  }
}
