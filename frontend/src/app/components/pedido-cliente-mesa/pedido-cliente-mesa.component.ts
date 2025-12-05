import { Component, inject, signal, computed, OnInit, OnDestroy, ChangeDetectionStrategy, PLATFORM_ID, AfterViewInit } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { PedidoMesaService, ItemPedidoMesaRequest, CriarPedidoMesaRequest } from '../../services/pedido-mesa.service';
import { Mesa } from '../../services/mesa.service';
import { Produto } from '../../services/produto.service';
import { DraggableScrollDirective } from './directives/draggable-scroll.directive';

import {
  useIdentificacaoCliente,
  useCarrinho,
  usePagamento,
  useCardapio,
  useFavoritos,
  useGoogleAuth,
  useInicio,
  useSucessoPedido,
  useMeusPedidos
} from './composables';

type EtapaPrincipal = 'identificacao' | 'cadastro' | 'cardapio' | 'sucesso';
type AbaCliente = 'inicio' | 'cardapio' | 'perfil';
type SecaoPerfil = 'principal' | 'favoritos' | 'pedidos' | 'senha';

/**
 * Componente de pedido para cliente via QR Code da mesa.
 * Orquestra os composables de identificação, carrinho, pagamento e cardápio.
 */
@Component({
  selector: 'app-pedido-cliente-mesa',
  standalone: true,
  imports: [CommonModule, FormsModule, DraggableScrollDirective],
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
  readonly abaAtual = signal<AbaCliente>('inicio');
  readonly enviando = signal(false);
  readonly secaoPerfil = signal<SecaoPerfil>('principal');

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
  readonly inicio = useInicio(
    this.mesaToken,
    () => this.identificacao.clienteIdentificado()?.id,
    () => this.favoritos.produtosFavoritos()
  );
  readonly sucesso = useSucessoPedido();
  readonly meusPedidos = useMeusPedidos(() => this.identificacao.clienteIdentificado()?.id);

  // ========== Estado para Seção de Senha ==========
  senhaAtual = '';
  novaSenha = '';
  confirmarSenha = '';
  readonly erroSenha = signal<string | null>(null);
  readonly salvandoSenha = signal(false);

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

  // Handler para back button do navegador
  private readonly boundHandlePopState = this.handlePopState.bind(this);

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

    // Listener para fechar carrinho no back button do celular
    window.addEventListener('popstate', this.boundHandlePopState);
  }

  ngAfterViewInit(): void {
    if (this.isBrowser) this.googleAuth.inicializar();
  }

  ngOnDestroy(): void {
    this.googleAuth.destroy();
    this.identificacao.destroy();
    this.sucesso.destroy();
    if (this.isBrowser) {
      window.removeEventListener('popstate', this.boundHandlePopState);
    }
  }

  /**
   * Handler para o evento popstate (back button)
   * Fecha o carrinho em vez de navegar para trás
   */
  private handlePopState(): void {
    if (this.carrinho.mostrarCarrinho()) {
      // Fecha o carrinho
      this.carrinho.fecharCarrinho();
      this.pagamento.resetarEtapa();
      // Adiciona nova entrada no histórico para manter a posição
      history.pushState({ carrinho: false }, '');
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

        // Se cliente já está identificado (restaurado do sessionStorage), vai direto para cardápio
        if (this.identificacao.clienteIdentificado()) {
          this.irParaCardapio();
        }
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
    this.inicio.carregar();
  }

  navegarPara(aba: AbaCliente | 'carrinho'): void {
    if (aba === 'carrinho') {
      this.carrinho.abrirCarrinho();
    } else {
      this.carrinho.fecharCarrinho();
      this.abaAtual.set(aba);
      // Reset seção do perfil quando navegar para ele
      if (aba === 'perfil') {
        this.secaoPerfil.set('principal');
      }
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
    // Adiciona entrada no histórico para capturar back button
    if (this.isBrowser) {
      history.pushState({ carrinho: true }, '');
    }
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

  // ========== Seções do Perfil ==========
  irParaMeusPedidos(): void {
    this.secaoPerfil.set('pedidos');
    this.meusPedidos.carregar();
    this.carregarAvaliacoesCliente();
  }

  adicionarAoCarrinhoRapido(produto: Produto): void {
    this.carrinho.adicionarRapido(produto);
    // Feedback visual pode ser adicionado aqui
  }

  async salvarSenha(): Promise<void> {
    this.erroSenha.set(null);

    // Validações
    if (!this.novaSenha || this.novaSenha.length < 6) {
      this.erroSenha.set('A senha deve ter pelo menos 6 caracteres');
      return;
    }

    if (this.novaSenha !== this.confirmarSenha) {
      this.erroSenha.set('As senhas não conferem');
      return;
    }

    const cliente = this.identificacao.clienteIdentificado();
    if (!cliente) return;

    if (cliente.temSenha && !this.senhaAtual) {
      this.erroSenha.set('Digite sua senha atual');
      return;
    }

    this.salvandoSenha.set(true);

    try {
      await firstValueFrom(
        this.pedidoMesaService.salvarSenhaCliente(
          cliente.id,
          this.novaSenha,
          cliente.temSenha ? this.senhaAtual : undefined
        )
      );

      // Sucesso - atualizar estado do cliente
      this.identificacao.atualizarTemSenha(true);

      // Limpar campos e voltar
      this.senhaAtual = '';
      this.novaSenha = '';
      this.confirmarSenha = '';
      this.secaoPerfil.set('principal');

      alert('Senha salva com sucesso!');
    } catch (err: unknown) {
      const httpError = err as { error?: { message?: string } };
      this.erroSenha.set(httpError.error?.message || 'Erro ao salvar senha');
    } finally {
      this.salvandoSenha.set(false);
    }
  }

  // ========== Avaliação de Produtos ==========
  // Chave: "pedidoId:produtoId" => nota (número de estrelas)
  private readonly avaliacoes = signal<Map<string, number>>(new Map());
  // Comentário por pedido (não por produto)
  private readonly comentariosPedido = signal<Map<string, string>>(new Map());
  // Feedback visual: pedidoId => true se comentário foi salvo
  readonly comentarioSalvo = signal<Map<string, boolean>>(new Map());
  readonly salvandoComentario = signal(false);
  // Modo de edição de avaliação por pedido
  readonly editandoAvaliacao = signal<Map<string, boolean>>(new Map());
  // Avaliação submetida (enviada pelo botão Enviar) por pedido
  private readonly avaliacaoSubmetida = signal<Map<string, boolean>>(new Map());

  private getAvaliacaoKey(pedidoId: string, produtoId: string): string {
    return `${pedidoId}:${produtoId}`;
  }

  getAvaliacaoProduto(produtoId: string): number {
    const pedido = this.meusPedidos.pedidoSelecionado();
    if (!pedido) return 0;
    // Usa o ID do pedido (UUID) como chave
    const key = this.getAvaliacaoKey(pedido.id, produtoId);
    return this.avaliacoes().get(key) || 0;
  }

  // Verifica se o pedido já foi avaliado E submetido
  pedidoJaAvaliado(): boolean {
    const pedido = this.meusPedidos.pedidoSelecionado();
    if (!pedido) return false;

    // Só considera "avaliado" se foi submetido pelo botão Enviar
    return this.avaliacaoSubmetida().get(pedido.id) || false;
  }

  // Verifica se está em modo de edição para o pedido atual
  isEditandoAvaliacao(): boolean {
    const pedido = this.meusPedidos.pedidoSelecionado();
    if (!pedido) return false;
    return this.editandoAvaliacao().get(pedido.id) || false;
  }

  // Ativa modo de edição
  editarAvaliacao(): void {
    const pedido = this.meusPedidos.pedidoSelecionado();
    if (!pedido) return;
    const novoStatus = new Map(this.editandoAvaliacao());
    novoStatus.set(pedido.id, true);
    this.editandoAvaliacao.set(novoStatus);
  }

  // Calcula média das avaliações do pedido
  getMediaAvaliacaoPedido(): number {
    const pedido = this.meusPedidos.pedidoSelecionado();
    if (!pedido || !pedido.itens || pedido.itens.length === 0) return 0;

    let soma = 0;
    let count = 0;
    for (const item of pedido.itens) {
      const key = this.getAvaliacaoKey(pedido.id, item.produtoId);
      const nota = this.avaliacoes().get(key) || 0;
      if (nota > 0) {
        soma += nota;
        count++;
      }
    }
    return count > 0 ? Math.round(soma / count) : 0;
  }

  // Getter para o comentário do pedido atual
  getComentarioPedido(): string {
    const pedido = this.meusPedidos.pedidoSelecionado();
    if (!pedido) return '';
    return this.comentariosPedido().get(pedido.id) || '';
  }

  // Verifica se o comentário do pedido atual foi salvo
  isComentarioSalvo(): boolean {
    const pedido = this.meusPedidos.pedidoSelecionado();
    if (!pedido) return false;
    return this.comentarioSalvo().get(pedido.id) || false;
  }

  // Setter para o comentário do pedido atual
  setComentarioPedido(comentario: string): void {
    const pedido = this.meusPedidos.pedidoSelecionado();
    if (!pedido) return;
    const novosComentarios = new Map(this.comentariosPedido());
    novosComentarios.set(pedido.id, comentario);
    this.comentariosPedido.set(novosComentarios);

    // Remove o status de "salvo" quando editar
    const novoStatus = new Map(this.comentarioSalvo());
    novoStatus.delete(pedido.id);
    this.comentarioSalvo.set(novoStatus);
  }

  avaliarProduto(produtoId: string, nota: number): void {
    const cliente = this.identificacao.clienteIdentificado();
    const pedido = this.meusPedidos.pedidoSelecionado();
    if (!cliente || !pedido) return;

    // Usa o ID do pedido (UUID) para a chave local e para enviar ao backend
    const key = this.getAvaliacaoKey(pedido.id, produtoId);

    // Atualiza localmente
    const novasAvaliacoes = new Map(this.avaliacoes());
    novasAvaliacoes.set(key, nota);
    this.avaliacoes.set(novasAvaliacoes);

    // Pega o comentário atual do pedido
    const comentario = this.comentariosPedido().get(pedido.id);

    // Envia para o backend com o ID do pedido (UUID)
    this.pedidoMesaService.avaliarProduto(cliente.id, produtoId, pedido.id, nota, comentario || undefined).subscribe({
      next: () => {
        // Sucesso - já está atualizado localmente
      },
      error: () => {
        console.error('Erro ao salvar avaliação');
      }
    });
  }

  salvarComentarioPedido(): void {
    const cliente = this.identificacao.clienteIdentificado();
    const pedido = this.meusPedidos.pedidoSelecionado();
    if (!cliente || !pedido) return;

    const comentario = this.comentariosPedido().get(pedido.id) || '';

    // Se não tem comentário, não precisa salvar
    if (!comentario.trim()) return;

    // Pega todos os produtos avaliados deste pedido para atualizar o comentário
    const itens = pedido.itens || [];
    if (itens.length === 0) return;

    // Atualiza o comentário no primeiro produto que tem avaliação
    for (const item of itens) {
      const key = this.getAvaliacaoKey(pedido.id, item.produtoId);
      const nota = this.avaliacoes().get(key);
      if (nota && nota > 0) {
        this.salvandoComentario.set(true);

        // Envia para o backend com o comentário atualizado
        this.pedidoMesaService.avaliarProduto(
          cliente.id,
          item.produtoId,
          pedido.id,
          nota,
          comentario
        ).subscribe({
          next: () => {
            this.salvandoComentario.set(false);
            // Marca como salvo
            const novoStatus = new Map(this.comentarioSalvo());
            novoStatus.set(pedido.id, true);
            this.comentarioSalvo.set(novoStatus);
          },
          error: () => {
            this.salvandoComentario.set(false);
            console.error('Erro ao salvar comentário');
          }
        });
        break; // Só precisa salvar uma vez
      }
    }

    // Se não tem nenhum produto avaliado, avalia o primeiro com nota 5 para poder salvar o comentário
    if (!itens.some(item => {
      const key = this.getAvaliacaoKey(pedido.id, item.produtoId);
      return (this.avaliacoes().get(key) || 0) > 0;
    }) && itens.length > 0) {
      const primeiroItem = itens[0];
      this.salvandoComentario.set(true);

      // Salva com nota 5 (avaliação positiva padrão para permitir comentário)
      this.pedidoMesaService.avaliarProduto(
        cliente.id,
        primeiroItem.produtoId,
        pedido.id,
        5,
        comentario
      ).subscribe({
        next: () => {
          this.salvandoComentario.set(false);
          // Atualiza localmente
          const novasAvaliacoes = new Map(this.avaliacoes());
          const key = this.getAvaliacaoKey(pedido.id, primeiroItem.produtoId);
          novasAvaliacoes.set(key, 5);
          this.avaliacoes.set(novasAvaliacoes);
          // Marca como salvo
          const novoStatus = new Map(this.comentarioSalvo());
          novoStatus.set(pedido.id, true);
          this.comentarioSalvo.set(novoStatus);
        },
        error: () => {
          this.salvandoComentario.set(false);
          console.error('Erro ao salvar comentário');
        }
      });
    }
  }

  // Verifica se tem pelo menos uma estrela marcada
  temAlgumaAvaliacao(): boolean {
    const pedido = this.meusPedidos.pedidoSelecionado();
    if (!pedido || !pedido.itens) return false;

    for (const item of pedido.itens) {
      const key = this.getAvaliacaoKey(pedido.id, item.produtoId);
      if ((this.avaliacoes().get(key) || 0) > 0) {
        return true;
      }
    }
    return false;
  }

  // Envia a avaliação e muda para modo visualização
  enviarAvaliacao(): void {
    const cliente = this.identificacao.clienteIdentificado();
    const pedido = this.meusPedidos.pedidoSelecionado();
    if (!cliente || !pedido) return;

    const comentario = this.comentariosPedido().get(pedido.id) || '';
    const itens = pedido.itens || [];
    if (itens.length === 0) return;

    this.salvandoComentario.set(true);

    // Salva todas as avaliações com o comentário
    let salvou = false;
    for (const item of itens) {
      const key = this.getAvaliacaoKey(pedido.id, item.produtoId);
      const nota = this.avaliacoes().get(key);
      if (nota && nota > 0) {
        this.pedidoMesaService.avaliarProduto(
          cliente.id,
          item.produtoId,
          pedido.id,
          nota,
          salvou ? undefined : comentario // Só envia comentário na primeira
        ).subscribe({
          next: () => {
            this.salvandoComentario.set(false);
            // Marca como salvo e sai do modo edição
            const novoStatusSalvo = new Map(this.comentarioSalvo());
            novoStatusSalvo.set(pedido.id, true);
            this.comentarioSalvo.set(novoStatusSalvo);

            // Marca como submetida
            const novoStatusSubmetida = new Map(this.avaliacaoSubmetida());
            novoStatusSubmetida.set(pedido.id, true);
            this.avaliacaoSubmetida.set(novoStatusSubmetida);

            // Sai do modo edição
            const novoStatusEditando = new Map(this.editandoAvaliacao());
            novoStatusEditando.delete(pedido.id);
            this.editandoAvaliacao.set(novoStatusEditando);
          },
          error: () => {
            this.salvandoComentario.set(false);
            console.error('Erro ao salvar avaliação');
          }
        });
        salvou = true;
      }
    }

    // Se não salvou nenhuma (não tinha estrelas), mostra erro
    if (!salvou) {
      this.salvandoComentario.set(false);
    }
  }

  // Carrega avaliações do cliente quando entra em Meus Pedidos
  carregarAvaliacoesCliente(): void {
    const cliente = this.identificacao.clienteIdentificado();
    if (!cliente) return;

    this.pedidoMesaService.buscarAvaliacoesCliente(cliente.id).subscribe({
      next: (avaliacoesList) => {
        const novasAvaliacoes = new Map<string, number>();
        const novosComentarios = new Map<string, string>();
        const pedidosComAvaliacao = new Set<string>();

        for (const avaliacao of avaliacoesList) {
          if (avaliacao.pedidoId && avaliacao.produtoId) {
            const key = this.getAvaliacaoKey(avaliacao.pedidoId, avaliacao.produtoId);
            novasAvaliacoes.set(key, avaliacao.nota);
            pedidosComAvaliacao.add(avaliacao.pedidoId);

            // Guarda o comentário por pedido (pega o primeiro que encontrar)
            if (avaliacao.comentario && !novosComentarios.has(avaliacao.pedidoId)) {
              novosComentarios.set(avaliacao.pedidoId, avaliacao.comentario);
            }
          }
        }

        this.avaliacoes.set(novasAvaliacoes);
        this.comentariosPedido.set(novosComentarios);

        // Marca pedidos que têm avaliação do backend como submetidos
        const novoStatusSubmetida = new Map<string, boolean>();
        for (const pedidoId of pedidosComAvaliacao) {
          novoStatusSubmetida.set(pedidoId, true);
        }
        this.avaliacaoSubmetida.set(novoStatusSubmetida);
      },
      error: () => {
        console.error('Erro ao carregar avaliações');
      }
    });
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
      next: (response) => {
        this.enviando.set(false);
        this.etapaAtual.set('sucesso');
        this.carrinho.limparCarrinho();
        this.pagamento.limparPagamentos();
        this.carrinho.fecharCarrinho();

        // Inicia acompanhamento do status do pedido
        if (response.id) {
          this.sucesso.iniciarAcompanhamento(response.id);
        }
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
    this.sucesso.limpar();
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
