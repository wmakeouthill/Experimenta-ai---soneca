import { Component, inject, signal, computed, OnInit, ChangeDetectionStrategy, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { PedidoMesaService, ItemPedidoMesaRequest, CriarPedidoMesaRequest } from '../../services/pedido-mesa.service';
import { Mesa } from '../../services/mesa.service';
import { Produto } from '../../services/produto.service';
import { Categoria } from '../../services/categoria.service';

interface ItemCarrinho {
    produto: Produto;
    quantidade: number;
    observacao: string;
}

interface ClienteIdentificado {
    id: string;
    nome: string;
    telefone: string;
    novoCliente: boolean;
}

interface GrupoCategoria {
    id: string;
    nome: string;
    produtos: Produto[];
}

type Etapa = 'identificacao' | 'cadastro' | 'cardapio' | 'sucesso';

@Component({
    selector: 'app-pedido-cliente-mesa',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './pedido-cliente-mesa.component.html',
    styleUrls: ['./pedido-cliente-mesa.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class PedidoClienteMesaComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly pedidoMesaService = inject(PedidoMesaService);
    private readonly platformId = inject(PLATFORM_ID);
    private readonly isBrowser = isPlatformBrowser(this.platformId);

    // Estado geral
    mesa = signal<Mesa | null>(null);
    carregando = signal(true);
    erro = signal<string | null>(null);
    etapaAtual = signal<Etapa>('identificacao');

    // Identificação do cliente
    private readonly _telefoneInput = signal('');
    private readonly _nomeInput = signal('');
    buscandoCliente = signal(false);
    clienteIdentificado = signal<ClienteIdentificado | null>(null);
    erroIdentificacao = signal<string | null>(null);

    // Cardápio
    categorias = signal<Categoria[]>([]);
    produtos = signal<Produto[]>([]);
    carregandoCardapio = signal(false);

    // Carrinho
    carrinho = signal<ItemCarrinho[]>([]);

    // Navegação do cardápio
    categoriaSelecionada = signal<string | null>(null);
    mostrarCarrinho = signal(false);
    mostrarDetalhesProduto = signal(false);
    produtoSelecionado = signal<Produto | null>(null);
    quantidadeTemp = signal(1);
    private readonly _observacaoTemp = signal('');

    // Envio do pedido
    enviando = signal(false);

    // ========== Getters e Setters para NgModel ==========
    get telefoneInputValue(): string {
        return this._telefoneInput();
    }
    set telefoneInputValue(value: string) {
        // Formata telefone enquanto digita
        const numeros = value.replace(/\D/g, '');
        if (numeros.length <= 11) {
            this._telefoneInput.set(this.formatarTelefoneInput(numeros));
        }
    }

    get nomeInputValue(): string {
        return this._nomeInput();
    }
    set nomeInputValue(value: string) {
        this._nomeInput.set(value);
    }

    get observacaoTempValue(): string {
        return this._observacaoTemp();
    }
    set observacaoTempValue(value: string) {
        this._observacaoTemp.set(value);
    }

    // Expor signals
    telefoneInput = this._telefoneInput.asReadonly();
    nomeInput = this._nomeInput.asReadonly();
    observacaoTemp = this._observacaoTemp.asReadonly();

    // ========== Computed ==========
    telefoneValido = computed(() => {
        const numeros = this._telefoneInput().replace(/\D/g, '');
        return numeros.length >= 10 && numeros.length <= 11;
    });

    nomeValido = computed(() => {
        return this._nomeInput().trim().length >= 2;
    });

    podeCadastrar = computed(() => {
        return this.telefoneValido() && this.nomeValido();
    });

    produtosFiltrados = computed(() => {
        const categoria = this.categoriaSelecionada();
        const todosProdutos = this.produtos();

        if (!categoria) {
            return todosProdutos.filter(p => p.disponivel);
        }

        return todosProdutos.filter(p => p.disponivel && p.categoria === categoria);
    });

    /**
     * Produtos agrupados por categoria para exibição quando "Todos" está selecionado.
     * Retorna um array de objetos { id, nome, produtos }.
     */
    produtosPorCategoria = computed((): GrupoCategoria[] => {
        const categoriaSelecionada = this.categoriaSelecionada();
        const todosProdutos = this.produtos().filter(p => p.disponivel);
        const categoriasAtivas = this.categorias().filter(c => c.ativa);

        // Se uma categoria específica está selecionada, não agrupa
        if (categoriaSelecionada) {
            return [];
        }

        // Agrupa produtos por categoria na ordem das categorias
        const grupos: GrupoCategoria[] = [];

        for (const categoria of categoriasAtivas) {
            const produtosDaCategoria = todosProdutos.filter(p => p.categoria === categoria.nome);
            if (produtosDaCategoria.length > 0) {
                grupos.push({
                    id: categoria.id,
                    nome: categoria.nome,
                    produtos: produtosDaCategoria
                });
            }
        }

        // Adiciona produtos sem categoria no final (se houver)
        const produtosSemCategoria = todosProdutos.filter(p =>
            !categoriasAtivas.some(c => c.nome === p.categoria)
        );
        if (produtosSemCategoria.length > 0) {
            grupos.push({
                id: 'outros',
                nome: 'Outros',
                produtos: produtosSemCategoria
            });
        }

        return grupos;
    });

    /**
     * Indica se deve mostrar produtos agrupados por categoria.
     */
    mostrarPorCategoria = computed(() => {
        return this.categoriaSelecionada() === null;
    });

    totalItens = computed(() => {
        return this.carrinho().reduce((acc, item) => acc + item.quantidade, 0);
    });

    totalCarrinho = computed(() => {
        return this.carrinho().reduce((acc, item) => acc + (item.produto.preco * item.quantidade), 0);
    });

    podeEnviarPedido = computed(() => {
        return this.carrinho().length > 0 && this.clienteIdentificado() !== null;
    });

    // ========== Lifecycle ==========
    ngOnInit(): void {
        // Só executa no browser para evitar problemas com SSR
        if (!this.isBrowser) {
            return;
        }

        const token = this.route.snapshot.paramMap.get('token');

        if (!token) {
            this.erro.set('Token da mesa não encontrado');
            this.carregando.set(false);
            return;
        }

        this.carregarMesa(token);
    }

    // ========== Carregamento inicial ==========
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
            error: (err) => {
                console.error('Erro ao buscar mesa:', err);
                this.erro.set('Mesa não encontrada ou indisponível');
                this.carregando.set(false);
            }
        });
    }

    // ========== Identificação do Cliente ==========
    buscarCliente(): void {
        if (!this.telefoneValido()) return;

        const telefone = this._telefoneInput().replace(/\D/g, '');
        this.buscandoCliente.set(true);
        this.erroIdentificacao.set(null);

        const mesaToken = this.mesa()?.qrCodeToken;
        if (!mesaToken) return;

        this.pedidoMesaService.buscarClientePorTelefone(mesaToken, telefone).subscribe({
            next: (cliente) => {
                this.buscandoCliente.set(false);
                this.clienteIdentificado.set({
                    id: cliente.id,
                    nome: cliente.nome,
                    telefone: cliente.telefone,
                    novoCliente: false
                });
                // Cliente encontrado, ir direto para o cardápio
                this.irParaCardapio();
            },
            error: (err) => {
                this.buscandoCliente.set(false);
                if (err.status === 404) {
                    // Cliente não encontrado, ir para cadastro
                    this.etapaAtual.set('cadastro');
                } else {
                    this.erroIdentificacao.set('Erro ao buscar cliente. Tente novamente.');
                }
            }
        });
    }

    cadastrarCliente(): void {
        if (!this.podeCadastrar()) return;

        const telefone = this._telefoneInput().replace(/\D/g, '');
        const nome = this._nomeInput().trim();
        this.buscandoCliente.set(true);
        this.erroIdentificacao.set(null);

        const mesaToken = this.mesa()?.qrCodeToken;
        if (!mesaToken) return;

        this.pedidoMesaService.cadastrarCliente(mesaToken, { nome, telefone }).subscribe({
            next: (cliente) => {
                this.buscandoCliente.set(false);
                this.clienteIdentificado.set({
                    id: cliente.id,
                    nome: cliente.nome,
                    telefone: cliente.telefone,
                    novoCliente: true
                });
                this.irParaCardapio();
            },
            error: (err) => {
                this.buscandoCliente.set(false);
                console.error('Erro ao cadastrar cliente:', err);
                this.erroIdentificacao.set('Erro ao cadastrar. Tente novamente.');
            }
        });
    }

    voltarParaIdentificacao(): void {
        this.etapaAtual.set('identificacao');
        this._nomeInput.set('');
        this.erroIdentificacao.set(null);
    }

    // ========== Cardápio ==========
    private irParaCardapio(): void {
        this.etapaAtual.set('cardapio');
        this.carregarCardapio();
    }

    private carregarCardapio(): void {
        const token = this.mesa()?.qrCodeToken;
        if (!token) return;

        this.carregandoCardapio.set(true);

        this.pedidoMesaService.buscarCardapio(token).subscribe({
            next: (cardapio) => {
                this.categorias.set(cardapio.categorias.filter(c => c.ativa));
                this.produtos.set(cardapio.produtos.filter(p => p.disponivel));
                this.carregandoCardapio.set(false);
            },
            error: (err) => {
                console.error('Erro ao carregar cardápio:', err);
                this.erro.set('Erro ao carregar o cardápio');
                this.carregandoCardapio.set(false);
            }
        });
    }

    selecionarCategoria(categoria: string | null): void {
        this.categoriaSelecionada.set(categoria);
    }

    // ========== Detalhes do Produto ==========
    abrirDetalhesProduto(produto: Produto): void {
        this.produtoSelecionado.set(produto);
        this.quantidadeTemp.set(1);
        this._observacaoTemp.set('');

        // Verificar se já está no carrinho
        const itemExistente = this.carrinho().find(item => item.produto.id === produto.id);
        if (itemExistente) {
            this.quantidadeTemp.set(itemExistente.quantidade);
            this._observacaoTemp.set(itemExistente.observacao);
        }

        this.mostrarDetalhesProduto.set(true);
    }

    fecharDetalhesProduto(): void {
        this.mostrarDetalhesProduto.set(false);
        this.produtoSelecionado.set(null);
    }

    incrementarQuantidade(): void {
        this.quantidadeTemp.update(q => q + 1);
    }

    decrementarQuantidade(): void {
        if (this.quantidadeTemp() > 1) {
            this.quantidadeTemp.update(q => q - 1);
        }
    }

    adicionarAoCarrinho(): void {
        const produto = this.produtoSelecionado();
        if (!produto) return;

        const carrinhoAtual = [...this.carrinho()];
        const indexExistente = carrinhoAtual.findIndex(item => item.produto.id === produto.id);

        if (indexExistente >= 0) {
            carrinhoAtual[indexExistente] = {
                ...carrinhoAtual[indexExistente],
                quantidade: this.quantidadeTemp(),
                observacao: this._observacaoTemp()
            };
        } else {
            carrinhoAtual.push({
                produto,
                quantidade: this.quantidadeTemp(),
                observacao: this._observacaoTemp()
            });
        }

        this.carrinho.set(carrinhoAtual);
        this.fecharDetalhesProduto();
    }

    // ========== Carrinho ==========
    removerDoCarrinho(produtoId: string): void {
        this.carrinho.update(items => items.filter(item => item.produto.id !== produtoId));
    }

    alterarQuantidadeCarrinho(produtoId: string, delta: number): void {
        this.carrinho.update(items => {
            return items
                .map(item => {
                    if (item.produto.id === produtoId) {
                        const novaQuantidade = item.quantidade + delta;
                        if (novaQuantidade <= 0) return null;
                        return { ...item, quantidade: novaQuantidade };
                    }
                    return item;
                })
                .filter((item): item is ItemCarrinho => item !== null);
        });
    }

    abrirCarrinho(): void {
        this.mostrarCarrinho.set(true);
    }

    fecharCarrinho(): void {
        this.mostrarCarrinho.set(false);
    }

    // ========== Envio do Pedido ==========
    enviarPedido(): void {
        const mesa = this.mesa();
        const cliente = this.clienteIdentificado();
        if (!mesa || !cliente || !this.podeEnviarPedido()) return;

        this.enviando.set(true);

        const itens: ItemPedidoMesaRequest[] = this.carrinho().map(item => ({
            produtoId: item.produto.id,
            quantidade: item.quantidade,
            observacoes: item.observacao || undefined
        }));

        const request: CriarPedidoMesaRequest = {
            mesaToken: mesa.qrCodeToken,
            clienteId: cliente.id,
            nomeCliente: cliente.nome,
            itens
        };

        this.pedidoMesaService.criarPedido(request).subscribe({
            next: () => {
                this.enviando.set(false);
                this.etapaAtual.set('sucesso');
                this.carrinho.set([]);
                this.mostrarCarrinho.set(false);
            },
            error: (err) => {
                console.error('Erro ao enviar pedido:', err);
                this.enviando.set(false);
                this.erro.set('Erro ao enviar o pedido. Tente novamente.');
            }
        });
    }

    novoPedido(): void {
        this.etapaAtual.set('cardapio');
        this.carrinho.set([]);
        this.erro.set(null);
    }

    trocarCliente(): void {
        this.clienteIdentificado.set(null);
        this._telefoneInput.set('');
        this._nomeInput.set('');
        this.carrinho.set([]);
        this.etapaAtual.set('identificacao');
    }

    // ========== Formatação ==========
    formatarPreco(valor: number): string {
        return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
    }

    private formatarTelefoneInput(numeros: string): string {
        if (numeros.length === 0) return '';
        if (numeros.length <= 2) return `(${numeros}`;
        if (numeros.length <= 6) return `(${numeros.slice(0, 2)}) ${numeros.slice(2)}`;
        if (numeros.length <= 10) return `(${numeros.slice(0, 2)}) ${numeros.slice(2, 6)}-${numeros.slice(6)}`;
        return `(${numeros.slice(0, 2)}) ${numeros.slice(2, 7)}-${numeros.slice(7, 11)}`;
    }

    formatarTelefone(telefone: string): string {
        const numeros = telefone.replace(/\D/g, '');
        if (numeros.length === 11) {
            return `(${numeros.slice(0, 2)}) ${numeros.slice(2, 7)}-${numeros.slice(7)}`;
        }
        if (numeros.length === 10) {
            return `(${numeros.slice(0, 2)}) ${numeros.slice(2, 6)}-${numeros.slice(6)}`;
        }
        return telefone;
    }
}
