import { signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { Produto } from '../../../services/produto.service';
import { MesaService } from '../../../services/mesa.service';
import { firstValueFrom, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

export interface ProdutoPopular extends Produto {
    quantidadeVendida?: number;
    quantidadeFavoritos?: number;
    mediaAvaliacao?: number;
    totalAvaliacoes?: number;
}

/**
 * Composable para gerenciar a aba Início no autoatendimento.
 * Carrega produtos mais populares, mais favoritados e bem avaliados.
 * Como autoatendimento não tem mesa, usa endpoints gerais ou ignora token.
 */
export function useAutoAtendimentoInicio() {
    const http = inject(HttpClient);
    const mesaService = inject(MesaService);

    // Estado
    const carregando = signal(false);
    const erro = signal<string | null>(null);
    const produtosMaisPedidos = signal<ProdutoPopular[]>([]);
    const produtosMaisFavoritados = signal<ProdutoPopular[]>([]);
    const produtosBemAvaliados = signal<ProdutoPopular[]>([]);

    /**
     * Carrega os produtos populares da API
     * Executa todas as chamadas em paralelo para melhor performance
     */
    async function carregar(): Promise<void> {
        carregando.set(true);
        erro.set(null);

        try {
            // Busca uma mesa ativa para obter um token válido
            const mesas = await firstValueFrom(mesaService.listar(true));
            if (!mesas || mesas.length === 0) {
                console.warn('Nenhuma mesa ativa encontrada, não é possível carregar produtos populares');
                produtosMaisPedidos.set([]);
                produtosMaisFavoritados.set([]);
                produtosBemAvaliados.set([]);
                return;
            }

            // Usa o token da primeira mesa ativa
            const token = mesas[0].qrCodeToken;

            // Carregar TODOS em paralelo (muito mais rápido!)
            await Promise.all([
                carregarMaisPedidos(token),
                carregarMaisFavoritados(token),
                carregarBemAvaliados(token)
            ]);
        } catch (e) {
            console.error('Erro ao carregar produtos populares:', e);
            erro.set('Erro ao carregar produtos');
        } finally {
            carregando.set(false);
        }
    }

    /**
     * Carrega os produtos mais pedidos (geral)
     * Usa endpoints públicos de mesa com token de uma mesa ativa
     */
    async function carregarMaisPedidos(token: string): Promise<void> {
        try {
            const produtos = await firstValueFrom(
                http.get<ProdutoPopular[]>(
                    `${environment.apiUrl}/public/mesa/${token}/produtos/mais-pedidos?limite=8`
                ).pipe(catchError(() => of([])))
            );

            produtosMaisPedidos.set(produtos || []);
        } catch (e) {
            console.warn('Endpoint mais-pedidos não disponível');
            produtosMaisPedidos.set([]);
        }
    }

    /**
     * Carrega os produtos mais favoritados (geral)
     */
    async function carregarMaisFavoritados(token: string): Promise<void> {
        try {
            const produtos = await firstValueFrom(
                http.get<ProdutoPopular[]>(
                    `${environment.apiUrl}/public/mesa/${token}/produtos/mais-favoritados?limite=20`
                ).pipe(catchError(() => of([])))
            );

            produtosMaisFavoritados.set(produtos || []);
        } catch (e) {
            console.warn('Endpoint mais-favoritados não disponível');
            produtosMaisFavoritados.set([]);
        }
    }

    /**
     * Carrega os produtos mais bem avaliados
     */
    async function carregarBemAvaliados(token: string): Promise<void> {
        try {
            const produtos = await firstValueFrom(
                http.get<ProdutoPopular[]>(
                    `${environment.apiUrl}/public/mesa/${token}/produtos/bem-avaliados?limite=8`
                ).pipe(catchError(() => of([])))
            );

            produtosBemAvaliados.set(produtos || []);
        } catch (e) {
            console.warn('Endpoint bem-avaliados não disponível');
            produtosBemAvaliados.set([]);
        }
    }

    return {
        // Estado (readonly)
        carregando: carregando.asReadonly(),
        erro: erro.asReadonly(),
        produtosMaisPedidos: produtosMaisPedidos.asReadonly(),
        produtosMaisFavoritados: produtosMaisFavoritados.asReadonly(),
        produtosBemAvaliados: produtosBemAvaliados.asReadonly(),

        // Ações
        carregar
    };
}

