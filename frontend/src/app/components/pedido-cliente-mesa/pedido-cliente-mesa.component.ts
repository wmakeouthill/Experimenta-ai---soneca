import { Component, inject, signal, computed, OnInit, OnDestroy, ChangeDetectionStrategy, PLATFORM_ID, ElementRef, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { PedidoMesaService, ItemPedidoMesaRequest, CriarPedidoMesaRequest } from '../../services/pedido-mesa.service';
import { GoogleSignInService } from '../../services/google-signin.service';
import { Mesa } from '../../services/mesa.service';
import { Produto } from '../../services/produto.service';

import {
    useIdentificacaoCliente,
    useCarrinho,
    usePagamento,
    useCardapio,
    useFavoritos,
    useClienteAuth
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
    private readonly googleService = inject(GoogleSignInService);
    private readonly platformId = inject(PLATFORM_ID);
    private readonly isBrowser = isPlatformBrowser(this.platformId);

    // ViewChild para o botão do Google
    @ViewChild('googleButtonRef') googleButtonRef?: ElementRef<HTMLDivElement>;

    // Subscription para credenciais do Google
    private googleCredentialSub?: Subscription;

    // Expor Math para o template
    protected readonly Math = Math;

    // ========== Estado Geral ==========
    mesa = signal<Mesa | null>(null);
    carregando = signal(true);
    erro = signal<string | null>(null);
    etapaAtual = signal<EtapaPrincipal>('identificacao');
    abaAtual = signal<AbaCliente>('cardapio');
    enviando = signal(false);

    // ========== Composables ==========
    private readonly mesaToken = () => this.mesa()?.qrCodeToken;

    readonly identificacao = useIdentificacaoCliente(this.mesaToken);
    readonly carrinhoState = useCarrinho();
    readonly pagamento = usePagamento(() => this.carrinhoState.totalValor());
    readonly cardapio = useCardapio(this.mesaToken);
    readonly favoritos = useFavoritos(
        () => this.identificacao.clienteIdentificado()?.id,
        () => this.cardapio.produtos()
    );
    readonly clienteAuth = useClienteAuth();

    // ========== Computed ==========
    readonly podeEnviarPedido = computed(() =>
        !this.carrinhoState.carrinhoVazio() && this.identificacao.clienteIdentificado() !== null
    );

    // ========== Bindings para NgModel ==========
    get telefoneInputValue(): string {
        return this.identificacao.getTelefone();
    }
    set telefoneInputValue(value: string) {
        this.identificacao.setTelefone(value);
    }

    get nomeInputValue(): string {
        return this.identificacao.getNome();
    }
    set nomeInputValue(value: string) {
        this.identificacao.setNome(value);
    }

    get observacaoTempValue(): string {
        return this.carrinhoState.getObservacao();
    }
    set observacaoTempValue(value: string) {
        this.carrinhoState.setObservacao(value);
    }

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
        // Inicializar botão do Google quando estiver na tela de identificação
        if (this.isBrowser) {
            this.inicializarBotaoGoogle();
        }
    }

    ngOnDestroy(): void {
        // Cleanup subscription do Google
        this.googleCredentialSub?.unsubscribe();
        this.clienteAuth.destroy();
    }

    // ========== Autenticação Google ==========
    private async inicializarBotaoGoogle(): Promise<void> {
        try {
            await this.googleService.initialize();

            // Escuta credenciais do Google
            this.googleCredentialSub = this.googleService.credential$.subscribe(async (token) => {
                // Se já tem cliente identificado, é vinculação
                if (this.identificacao.clienteIdentificado()) {
                    await this.processarVinculacaoGoogle(token);
                } else {
                    // Senão é login
                    await this.processarLoginGoogle(token);
                }
            });
        } catch (error) {
            console.error('Erro ao inicializar Google Sign-In:', error);
        }
    }

    async loginComGoogle(): Promise<void> {
        // Mostra o prompt do Google One Tap
        this.googleService.showOneTap();
    }

    private async processarLoginGoogle(idToken: string): Promise<void> {
        try {
            const success = await this.clienteAuth.loginComGoogle(idToken);
            if (success) {
                const cliente = this.clienteAuth.cliente();
                if (cliente) {
                    // Definir o cliente identificado e ir para o cardápio
                    this.identificacao.setClienteFromGoogle({
                        id: cliente.id,
                        nome: cliente.nome,
                        telefone: cliente.telefone || ''
                    });
                    this.irParaCardapio();
                }
            }
        } catch (error) {
            console.error('Erro no login com Google:', error);
        }
    }

    private async processarVinculacaoGoogle(idToken: string): Promise<void> {
        try {
            await this.clienteAuth.vincularGoogle(idToken);
        } catch (error) {
            console.error('Erro ao vincular Google:', error);
        }
    }

    async vincularGoogle(): Promise<void> {
        // Mostra o prompt do Google para vincular conta
        this.googleService.showOneTap();

        // A subscription já existe e vai chamar processarVinculacaoGoogle
        // quando o usuário selecionar a conta
    }

    async desvincularGoogle(): Promise<void> {
        if (confirm('Deseja desvincular sua conta Google?')) {
            await this.clienteAuth.desvincularGoogle();
        }
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
        this.carrinhoState.limparCarrinho();
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
            this.carrinhoState.abrirCarrinho();
        } else {
            this.carrinhoState.fecharCarrinho();
            this.abaAtual.set(aba);
        }
    }

    // ========== Ações do Cardápio ==========
    selecionarCategoria(categoria: string | null): void {
        this.cardapio.selecionarCategoria(categoria);
    }

    abrirDetalhesProduto(produto: Produto): void {
        this.carrinhoState.abrirDetalhes(produto);
    }

    fecharDetalhesProduto(): void {
        this.carrinhoState.fecharDetalhes();
    }

    incrementarQuantidade(): void {
        this.carrinhoState.incrementarQuantidade();
    }

    decrementarQuantidade(): void {
        this.carrinhoState.decrementarQuantidade();
    }

    adicionarAoCarrinho(): void {
        this.carrinhoState.adicionarAoCarrinho();
    }

    // ========== Ações de Favoritos ==========
    toggleFavorito(produtoId: string, event?: Event): void {
        event?.stopPropagation();
        this.favoritos.toggle(produtoId);
    }

    // ========== Ações do Carrinho ==========
    removerDoCarrinho(produtoId: string): void {
        this.carrinhoState.removerDoCarrinho(produtoId);
    }

    alterarQuantidadeCarrinho(produtoId: string, delta: number): void {
        this.carrinhoState.alterarQuantidade(produtoId, delta);
    }

    abrirCarrinho(): void {
        this.carrinhoState.abrirCarrinho();
        this.pagamento.resetarEtapa();
    }

    fecharCarrinho(): void {
        this.carrinhoState.fecharCarrinho();
        this.pagamento.resetarEtapa();
    }

    // ========== Ações de Pagamento ==========
    avancarParaPagamento(): void {
        this.pagamento.avancarParaPagamento();
    }

    voltarParaItens(): void {
        this.pagamento.voltarParaItens();
    }

    irParaConfirmacao(): void {
        this.pagamento.irParaConfirmacao();
    }

    voltarParaPagamento(): void {
        this.pagamento.voltarParaPagamento();
    }

    togglePagamentoDividido(): void {
        this.pagamento.toggleDividido();
    }

    selecionarMeioPagamento(tipo: 'PIX' | 'CARTAO_CREDITO' | 'CARTAO_DEBITO' | 'DINHEIRO'): void {
        this.pagamento.selecionarMeio(tipo);
    }

    atualizarValorMeioPagamento(tipo: 'PIX' | 'CARTAO_CREDITO' | 'CARTAO_DEBITO' | 'DINHEIRO', valor: number): void {
        this.pagamento.atualizarValorMeio(tipo, valor);
    }

    meioPagamentoSelecionado(tipo: string): boolean {
        return this.pagamento.meioPagamentoSelecionado(tipo);
    }

    getValorMeioPagamento(tipo: string): number {
        return this.pagamento.getValorMeio(tipo);
    }

    getTotalAlocado(): number {
        return this.pagamento.totalAlocado();
    }

    getValorRestante(): number {
        return this.pagamento.valorRestante();
    }

    pagamentoValido(): boolean {
        return this.pagamento.pagamentoValido();
    }

    getNomeMeioPagamento(tipo: string): string {
        return this.pagamento.getNomeMeio(tipo);
    }

    getIconeMeioPagamento(tipo: string): string {
        return this.pagamento.getIconeMeio(tipo);
    }

    // ========== Envio do Pedido ==========
    enviarPedido(): void {
        const mesa = this.mesa();
        const cliente = this.identificacao.clienteIdentificado();
        if (!mesa || !cliente || !this.podeEnviarPedido() || !this.pagamentoValido()) return;

        this.enviando.set(true);

        const itens: ItemPedidoMesaRequest[] = this.carrinhoState.itens().map(item => ({
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
                this.carrinhoState.limparCarrinho();
                this.pagamento.limparPagamentos();
                this.carrinhoState.fecharCarrinho();
            },
            error: () => {
                this.enviando.set(false);
                this.erro.set('Erro ao enviar o pedido. Tente novamente.');
            }
        });
    }

    novoPedido(): void {
        this.etapaAtual.set('cardapio');
        this.carrinhoState.limparCarrinho();
        this.erro.set(null);
    }

    // ========== Formatação ==========
    formatarPreco(valor: number): string {
        return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
    }

    formatarTelefone(telefone: string): string {
        return this.identificacao.formatarTelefone(telefone);
    }
}
