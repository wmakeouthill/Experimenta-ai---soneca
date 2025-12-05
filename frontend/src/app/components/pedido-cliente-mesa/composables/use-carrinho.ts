import { signal, computed, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Produto } from '../../../services/produto.service';

const CARRINHO_STORAGE_KEY = 'pedido-mesa-carrinho';

export interface ItemCarrinho {
    produto: Produto;
    quantidade: number;
    observacao: string;
}

/**
 * Composable para gerenciar o carrinho de compras.
 * Responsabilidade única: adicionar, remover e controlar itens do carrinho.
 * Persiste no sessionStorage para manter entre atualizações de página.
 */
export function useCarrinho() {
    const platformId = inject(PLATFORM_ID);
    const isBrowser = isPlatformBrowser(platformId);

    // Restaura carrinho do sessionStorage
    function restaurarCarrinho(): ItemCarrinho[] {
        if (!isBrowser) return [];
        try {
            const stored = sessionStorage.getItem(CARRINHO_STORAGE_KEY);
            if (stored) {
                return JSON.parse(stored) as ItemCarrinho[];
            }
        } catch {
            // Ignora erros de parse
        }
        return [];
    }

    // Persiste carrinho no sessionStorage
    function persistirCarrinho(items: ItemCarrinho[]): void {
        if (!isBrowser) return;
        try {
            sessionStorage.setItem(CARRINHO_STORAGE_KEY, JSON.stringify(items));
        } catch {
            // Ignora erros de storage
        }
    }

    // Estado
    const itens = signal<ItemCarrinho[]>(restaurarCarrinho());
    const produtoSelecionado = signal<Produto | null>(null);
    const quantidadeTemp = signal(1);
    const _observacaoTemp = signal('');
    const mostrarDetalhes = signal(false);
    const mostrarCarrinho = signal(false);

    // Computed
    const totalItens = computed(() =>
        itens().reduce((total, item) => total + item.quantidade, 0)
    );

    const totalValor = computed(() =>
        itens().reduce((total, item) => total + (item.produto.preco * item.quantidade), 0)
    );

    const carrinhoVazio = computed(() => itens().length === 0);

    const podeEnviarPedido = computed(() =>
        itens().length > 0
    );

    // Getters/Setters para binding
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

        const itemExistente = itens().find(item => item.produto.id === produto.id);
        if (itemExistente) {
            quantidadeTemp.set(itemExistente.quantidade);
            _observacaoTemp.set(itemExistente.observacao);
        }

        mostrarDetalhes.set(true);
    }

    function fecharDetalhes(): void {
        mostrarDetalhes.set(false);
        produtoSelecionado.set(null);
    }

    function incrementarQuantidade(): void {
        quantidadeTemp.update(q => q + 1);
    }

    function decrementarQuantidade(): void {
        if (quantidadeTemp() > 1) {
            quantidadeTemp.update(q => q - 1);
        }
    }

    function adicionarAoCarrinho(): void {
        const produto = produtoSelecionado();
        if (!produto) return;

        const itensAtuais = [...itens()];
        const indexExistente = itensAtuais.findIndex(item => item.produto.id === produto.id);

        if (indexExistente >= 0) {
            itensAtuais[indexExistente] = {
                ...itensAtuais[indexExistente],
                quantidade: quantidadeTemp(),
                observacao: _observacaoTemp()
            };
        } else {
            itensAtuais.push({
                produto,
                quantidade: quantidadeTemp(),
                observacao: _observacaoTemp()
            });
        }

        itens.set(itensAtuais);
        fecharDetalhes();
    }

    function removerDoCarrinho(produtoId: string): void {
        itens.update(lista => lista.filter(item => item.produto.id !== produtoId));
    }

    function alterarQuantidade(produtoId: string, delta: number): void {
        itens.update(lista => {
            return lista
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

    function abrirCarrinho(): void {
        mostrarCarrinho.set(true);
    }

    function fecharCarrinho(): void {
        mostrarCarrinho.set(false);
    }

    function limparCarrinho(): void {
        itens.set([]);
    }

    return {
        // Estado
        itens: itens.asReadonly(),
        produtoSelecionado: produtoSelecionado.asReadonly(),
        quantidadeTemp: quantidadeTemp.asReadonly(),
        mostrarDetalhes: mostrarDetalhes.asReadonly(),
        mostrarCarrinho,

        // Computed
        totalItens,
        totalValor,
        carrinhoVazio,
        podeEnviarPedido,

        // Getters/Setters
        getObservacao,
        setObservacao,

        // Ações
        abrirDetalhes,
        fecharDetalhes,
        incrementarQuantidade,
        decrementarQuantidade,
        adicionarAoCarrinho,
        removerDoCarrinho,
        alterarQuantidade,
        abrirCarrinho,
        fecharCarrinho,
        limparCarrinho
    };
}
