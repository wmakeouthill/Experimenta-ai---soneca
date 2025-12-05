import { Component, inject, signal, computed, OnInit, OnDestroy, ChangeDetectionStrategy, PLATFORM_ID, AfterViewInit } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { PedidoMesaService, ItemPedidoMesaRequest, CriarPedidoMesaRequest } from '../../services/pedido-mesa.service';
import { Mesa } from '../../services/mesa.service';
import { Produto } from '../../services/produto.service';

import {
  useIdentificacaoCliente,
  useCarrinho,
  usePagamento,
  useCardapio,
  useFavoritos,
  useGoogleAuth
} from './composables';

type EtapaPrincipal = 'identificacao' | 'cadastro' | 'cardapio' | 'sucesso';
type AbaCliente = 'inicio' | 'cardapio' | 'perfil';

/**
 * Componente de pedido para cliente via QR Code da mesa.
 * Orquestra os composables de identificação, carrinho, pagamento e cardápio.
 */
@Component({
  selector: 'app-pedido-cliente-mesa',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './pedido-cliente-mesa.component.html',
  styleUrls: [
    './styles/base.css',
    './styles/identificacao.css',
    './styles/cardapio.css',
    './styles/modal.css',
    './styles/carrinho.css',
    './styles/abas.css',
    './styles/pagamento.css',
    './styles/responsive.css'
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PedidoClienteMesaComponent implements OnInit, OnDestroy, AfterViewInit {
  private readonly route = inject(ActivatedRoute);
  private readonly pedidoMesaService = inject(PedidoMesaService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  protected readonly Math = Math;

  // ========== Estado Geral ==========
  readonly mesa = signal<Mesa | null>(null);
  readonly carregando = signal(true);
  readonly erro = signal<string | null>(null);
  readonly etapaAtual = signal<EtapaPrincipal>('identificacao');
  readonly abaAtual = signal<AbaCliente>('cardapio');
  readonly enviando = signal(false);

  // ========== Composables ==========
  private readonly mesaToken = () => this.mesa()?.qrCodeToken;

  readonly identificacao = useIdentificacaoCliente(this.mesaToken);
  readonly carrinho = useCarrinho();
  readonly pagamento = usePagamento(() => this.carrinho.totalValor());
  readonly cardapio = useCardapio(this.mesaToken);
  readonly favoritos = useFavoritos(
    () => this.identificacao.clienteIdentificado()?.id,
    () => this.cardapio.produtos()
  );
  readonly googleAuth = useGoogleAuth(
    () => this.identificacao.clienteIdentificado(),
    (cliente) => {
      this.identificacao.setClienteFromGoogle(cliente);
      this.irParaCardapio();
    }
  );

  // ========== Computed ==========
  readonly podeEnviarPedido = computed(() =>
    !this.carrinho.carrinhoVazio() && this.identificacao.clienteIdentificado() !== null
  );

  // ========== Bindings para NgModel ==========
  get telefoneInputValue(): string { return this.identificacao.getTelefone(); }
  set telefoneInputValue(value: string) { this.identificacao.setTelefone(value); }

  get nomeInputValue(): string { return this.identificacao.getNome(); }
  set nomeInputValue(value: string) { this.identificacao.setNome(value); }

  get observacaoTempValue(): string { return this.carrinho.getObservacao(); }
  set observacaoTempValue(value: string) { this.carrinho.setObservacao(value); }

  // ========== Lifecycle ==========
  ngOnInit(): void {
    if (!this.isBrowser) return;
    const token = this.route.snapshot.paramMap.get('token');
    if (!token) {
      this.erro.set('Token da mesa não encontrado');
      this.carregando.set(false);
      return;
    }
    this.carregarMesa(token);
  }

  ngAfterViewInit(): void {
    if (this.isBrowser) this.googleAuth.inicializar();
  }

  ngOnDestroy(): void {
    this.googleAuth.destroy();
  }

  // ========== Ações Gerais ==========
  private carregarMesa(token: string): void {
    this.carregando.set(true);
    this.erro.set(null);

    this.pedidoMesaService.buscarMesa(token).subscribe({
      next: (mesa) => {
        if (!mesa.ativa) {
          this.erro.set('Esta mesa não está ativa no momento');
          this.carregando.set(false);
          return;
        }
        this.mesa.set(mesa);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set('Mesa não encontrada ou indisponível');
        this.carregando.set(false);
      }
    });
  }

  // ========== Ações de Identificação ==========
  buscarCliente(): void {
    this.identificacao.buscarCliente(() => this.irParaCardapio());
  }

  cadastrarCliente(): void {
    this.identificacao.cadastrarCliente(() => this.irParaCardapio());
  }

  voltarParaIdentificacao(): void {
    this.identificacao.voltarParaIdentificacao();
    this.etapaAtual.set('identificacao');
  }

  trocarCliente(): void {
    this.identificacao.trocarCliente();
    this.carrinho.limparCarrinho();
    this.etapaAtual.set('identificacao');
  }

  // ========== Navegação ==========
  private irParaCardapio(): void {
    this.etapaAtual.set('cardapio');
    this.cardapio.carregar();
    this.favoritos.carregar();
  }

  navegarPara(aba: AbaCliente | 'carrinho'): void {
    if (aba === 'carrinho') {
      this.carrinho.abrirCarrinho();
    } else {
      this.carrinho.fecharCarrinho();
      this.abaAtual.set(aba);
    }
  }

  // ========== Ações do Cardápio ==========
  abrirDetalhesProduto(produto: Produto): void {
    this.carrinho.abrirDetalhes(produto);
  }

  // ========== Ações de Favoritos ==========
  toggleFavorito(produtoId: string, event?: Event): void {
    event?.stopPropagation();
    this.favoritos.toggle(produtoId);
  }

  // ========== Ações do Carrinho ==========
  abrirCarrinho(): void {
    this.carrinho.abrirCarrinho();
    this.pagamento.resetarEtapa();
  }

  fecharCarrinho(): void {
    this.carrinho.fecharCarrinho();
    this.pagamento.resetarEtapa();
  }

  // ========== Google Auth ==========
  loginComGoogle(): void {
    this.googleAuth.abrirPrompt();
  }

  vincularGoogle(): void {
    this.googleAuth.abrirPrompt();
  }

  async desvincularGoogle(): Promise<void> {
    if (confirm('Deseja desvincular sua conta Google?')) {
      await this.googleAuth.desvincular();
    }
  }

  // ========== Envio do Pedido ==========
  enviarPedido(): void {
    const mesa = this.mesa();
    const cliente = this.identificacao.clienteIdentificado();
    if (!mesa || !cliente || !this.podeEnviarPedido() || !this.pagamento.pagamentoValido()) return;

    this.enviando.set(true);

    const itens: ItemPedidoMesaRequest[] = this.carrinho.itens().map(item => ({
      produtoId: item.produto.id,
      quantidade: item.quantidade,
      observacoes: item.observacao || undefined
    }));

    const meiosPagamento = this.pagamento.meiosSelecionados().map(m => ({
      meioPagamento: m.tipo,
      valor: m.valor
    }));

    const request: CriarPedidoMesaRequest = {
      mesaToken: mesa.qrCodeToken,
      clienteId: cliente.id,
      nomeCliente: cliente.nome,
      itens,
      meiosPagamento
    };

    this.pedidoMesaService.criarPedido(request).subscribe({
      next: () => {
        this.enviando.set(false);
        this.etapaAtual.set('sucesso');
        this.carrinho.limparCarrinho();
        this.pagamento.limparPagamentos();
        this.carrinho.fecharCarrinho();
      },
      error: () => {
        this.enviando.set(false);
        this.erro.set('Erro ao enviar o pedido. Tente novamente.');
      }
    });
  }

  novoPedido(): void {
    this.etapaAtual.set('cardapio');
    this.carrinho.limparCarrinho();
    this.erro.set(null);
  }

  // ========== Formatação ==========
  formatarPreco(valor: number): string {
    return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  formatarTelefone(telefone: string): string {
    return this.identificacao.formatarTelefone(telefone);
  }

  // ========== Utilitários ==========
  onImageError(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (img) img.style.display = 'none';
  }
}
