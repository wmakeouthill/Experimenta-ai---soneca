import { signal, computed, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Produto } from '../../../services/produto.service';
import { Adicional } from '../../../services/adicional.service';

const CARRINHO_TOTEM_STORAGE_KEY = 'autoatendimento-carrinho';

export interface ItemAdicionalTotem {
    adicional: Adicional;
    quantidade: number;
}

export interface ItemCarrinhoTotem {
    produto: Produto;
    quantidade: number;
    observacao: string;
    adicionais: ItemAdicionalTotem[];
}

/**
 * Composable para gerenciar o carrinho do auto atendimento.
 * Responsabilidade única: adicionar, remover e controlar itens do carrinho.
 * Usa sessionStorage para manter o carrinho durante a sessão do totem.
 */
export function useAutoAtendimentoCarrinho() {
    const platformId = inject(PLATFORM_ID);
    const isBrowser = isPlatformBrowser(platformId);

    // Restaura carrinho do sessionStorage
    function restaurarCarrinho(): ItemCarrinhoTotem[] {
        if (!isBrowser) return [];
        try {
            const stored = sessionStorage.getItem(CARRINHO_TOTEM_STORAGE_KEY);
            if (stored) {
                return JSON.parse(stored) as ItemCarrinhoTotem[];
            }
        } catch {
            // Ignora erros de parse
        }
        return [];
    }

    // Persiste carrinho no sessionStorage
    function persistirCarrinho(items: ItemCarrinhoTotem[]): void {
        if (!isBrowser) return;
        try {
            sessionStorage.setItem(CARRINHO_TOTEM_STORAGE_KEY, JSON.stringify(items));
        } catch {
            // Ignora erros de storage
        }
    }

    // Estado
    const itens = signal<ItemCarrinhoTotem[]>(restaurarCarrinho());
    const produtoSelecionado = signal<Produto | null>(null);
    const quantidadeTemp = signal(1);
    const _observacaoTemp = signal('');
    const mostrarDetalhes = signal(false);
    const mostrarCarrinho = signal(false);

    // Estado para adicionais
    const adicionaisDisponiveis = signal<Adicional[]>([]);
    const adicionaisSelecionados = signal<ItemAdicionalTotem[]>([]);
    const carregandoAdicionais = signal(false);
    const adicionaisExpandido = signal(false);

    // Computed
    const totalItens = computed(() =>
        itens().reduce((total, item) => total + item.quantidade, 0)
    );

    const totalValor = computed(() =>
        itens().reduce((total, item) => {
            let subtotal = item.produto.preco * item.quantidade;
            if (item.adicionais && item.adicionais.length > 0) {
                subtotal += item.adicionais.reduce((acc, ad) =>
                    acc + (ad.adicional.preco * ad.quantidade * item.quantidade), 0);
            }
            return total + subtotal;
        }, 0)
    );

    const subtotalAdicionais = computed(() => {
        return adicionaisSelecionados().reduce((acc, ad) =>
            acc + (ad.adicional.preco * ad.quantidade), 0);
    });

    const precoTotalItemModal = computed(() => {
        const produto = produtoSelecionado();
        if (!produto) return 0;
        const precoBase = produto.preco + subtotalAdicionais();
        return precoBase * quantidadeTemp();
    });

    const carrinhoVazio = computed(() => itens().length === 0);

    const podeEnviarPedido = computed(() => itens().length > 0);

    // Getters/Setters
    function getObservacao(): string {
        return _observacaoTemp();
    }

    function setObservacao(value: string): void {
        _observacaoTemp.set(value);
    }

    // Ações
    function abrirDetalhes(produto: Produto): void {
        produtoSelecionado.set(produto);
        quantidadeTemp.set(1);
        _observacaoTemp.set('');
        adicionaisSelecionados.set([]);
        adicionaisExpandido.set(false);
        mostrarDetalhes.set(true);
    }

    function fecharDetalhes(): void {
        mostrarDetalhes.set(false);
        produtoSelecionado.set(null);
        adicionaisDisponiveis.set([]);
        adicionaisSelecionados.set([]);
        adicionaisExpandido.set(false);
    }

    function setAdicionaisDisponiveis(adicionais: Adicional[]): void {
        adicionaisDisponiveis.set(adicionais);
    }

    function setCarregandoAdicionais(loading: boolean): void {
        carregandoAdicionais.set(loading);
    }

    function toggleAdicionaisExpandido(): void {
        adicionaisExpandido.update(v => !v);
    }

    function adicionarAdicional(adicional: Adicional): void {
        const atuais = adicionaisSelecionados();
        const index = atuais.findIndex(a => a.adicional.id === adicional.id);
        if (index >= 0) {
            const novos = [...atuais];
            novos[index] = { ...novos[index], quantidade: novos[index].quantidade + 1 };
            adicionaisSelecionados.set(novos);
        } else {
            adicionaisSelecionados.set([...atuais, { adicional, quantidade: 1 }]);
        }
    }

    function removerAdicional(adicionalId: string): void {
        const atuais = adicionaisSelecionados();
        const index = atuais.findIndex(a => a.adicional.id === adicionalId);
        if (index >= 0) {
            const novos = [...atuais];
            if (novos[index].quantidade > 1) {
                novos[index] = { ...novos[index], quantidade: novos[index].quantidade - 1 };
            } else {
                novos.splice(index, 1);
            }
            adicionaisSelecionados.set(novos);
        }
    }

    function isAdicionalSelecionado(adicionalId: string): boolean {
        return adicionaisSelecionados().some(a => a.adicional.id === adicionalId);
    }

    function toggleAdicional(adicional: Adicional): void {
        if (isAdicionalSelecionado(adicional.id)) {
            removerAdicional(adicional.id);
        } else {
            adicionarAdicional(adicional);
        }
    }

    function incrementarAdicional(adicionalId: string): void {
        const atuais = adicionaisSelecionados();
        const index = atuais.findIndex(a => a.adicional.id === adicionalId);
        if (index >= 0) {
            const novos = [...atuais];
            novos[index] = { ...novos[index], quantidade: novos[index].quantidade + 1 };
            adicionaisSelecionados.set(novos);
        }
    }

    function decrementarAdicional(adicionalId: string): void {
        removerAdicional(adicionalId);
    }

    function getQuantidadeAdicional(adicionalId: string): number {
        const item = adicionaisSelecionados().find(a => a.adicional.id === adicionalId);
        return item?.quantidade ?? 0;
    }

    function incrementarQuantidade(): void {
        quantidadeTemp.update(q => q + 1);
    }

    function decrementarQuantidade(): void {
        quantidadeTemp.update(q => Math.max(1, q - 1));
    }

    const chaveAdicionais = (lista: ItemAdicionalTotem[] = []) =>
        lista.map(a => `${a.adicional.id}x${a.quantidade}`).sort().join();

    /** Mesma personalização soma na linha existente; diferente vira outra linha (igual à mesa). */
    function adicionarLinha(novo: ItemCarrinhoTotem): void {
        const lista = [...itens()];
        const i = lista.findIndex(item =>
            item.produto.id === novo.produto.id &&
            item.observacao.trim() === novo.observacao.trim() &&
            chaveAdicionais(item.adicionais) === chaveAdicionais(novo.adicionais));
        if (i >= 0) {
            lista[i] = { ...lista[i], quantidade: lista[i].quantidade + novo.quantidade };
        } else {
            lista.push(novo);
        }
        itens.set(lista);
        persistirCarrinho(lista);
    }

    function confirmarProduto(): void {
        const produto = produtoSelecionado();
        if (!produto) return;

        adicionarLinha({
            produto,
            quantidade: quantidadeTemp(),
            observacao: _observacaoTemp(),
            adicionais: [...adicionaisSelecionados()]
        });
        fecharDetalhes();
    }

    function adicionarRapido(produto: Produto): void {
        adicionarLinha({ produto, quantidade: 1, observacao: '', adicionais: [] });
    }

    // Por índice: o mesmo produto pode estar em mais de uma linha
    function removerItem(index: number): void {
        const novosItens = itens().filter((_, i) => i !== index);
        itens.set(novosItens);
        persistirCarrinho(novosItens);
    }

    function atualizarQuantidadeItem(index: number, novaQuantidade: number): void {
        if (novaQuantidade <= 0) {
            removerItem(index);
            return;
        }

        const novosItens = itens().map((item, i) =>
            i === index ? { ...item, quantidade: novaQuantidade } : item);

        itens.set(novosItens);
        persistirCarrinho(novosItens);
    }

    function abrirCarrinho(): void {
        mostrarCarrinho.set(true);
    }

    function fecharCarrinho(): void {
        mostrarCarrinho.set(false);
    }

    function limparCarrinho(): void {
        itens.set([]);
        persistirCarrinho([]);
    }

    return {
        // Estado
        itens: itens.asReadonly(),
        produtoSelecionado: produtoSelecionado.asReadonly(),
        quantidadeTemp: quantidadeTemp.asReadonly(),
        mostrarDetalhes: mostrarDetalhes.asReadonly(),
        mostrarCarrinho: mostrarCarrinho.asReadonly(),
        adicionaisDisponiveis: adicionaisDisponiveis.asReadonly(),
        adicionaisSelecionados: adicionaisSelecionados.asReadonly(),
        carregandoAdicionais: carregandoAdicionais.asReadonly(),
        adicionaisExpandido: adicionaisExpandido.asReadonly(),

        // Computed
        totalItens,
        totalValor,
        subtotalAdicionais,
        precoTotalItemModal,
        carrinhoVazio,
        podeEnviarPedido,

        // Getters/Setters
        getObservacao,
        setObservacao,

        // Ações
        abrirDetalhes,
        fecharDetalhes,
        setAdicionaisDisponiveis,
        setCarregandoAdicionais,
        toggleAdicionaisExpandido,
        adicionarAdicional,
        removerAdicional,
        isAdicionalSelecionado,
        toggleAdicional,
        incrementarAdicional,
        decrementarAdicional,
        getQuantidadeAdicional,
        incrementarQuantidade,
        decrementarQuantidade,
        confirmarProduto,
        adicionarRapido,
        removerItem,
        atualizarQuantidadeItem,
        abrirCarrinho,
        fecharCarrinho,
        limparCarrinho
    };
}
