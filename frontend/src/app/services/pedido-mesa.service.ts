import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Mesa } from './mesa.service';
import { Produto } from './produto.service';
import { Categoria } from './categoria.service';

export interface ItemPedidoMesaRequest {
    produtoId: string;
    quantidade: number;
    observacoes?: string;
}

export interface MeioPagamentoMesaRequest {
    meioPagamento: 'PIX' | 'CARTAO_CREDITO' | 'CARTAO_DEBITO' | 'DINHEIRO';
    valor: number;
}

export interface CriarPedidoMesaRequest {
    mesaToken: string;
    clienteId: string;
    nomeCliente: string;
    itens: ItemPedidoMesaRequest[];
    meiosPagamento?: MeioPagamentoMesaRequest[];
}

export interface PedidoMesaResponse {
    id: string;
    mesaId: string;
    nomeClienteMesa: string;
    status: string;
    itens: {
        produtoId: string;
        nomeProduto: string;
        quantidade: number;
        precoUnitario: number;
        observacao?: string;
    }[];
    total: number;
    createdAt: string;
}

export interface CardapioPublico {
    categorias: Categoria[];
    produtos: Produto[];
}

export interface ClientePublico {
    id: string;
    nome: string;
    telefone: string;
    fotoUrl?: string;
}

export interface CadastrarClienteRequest {
    nome: string;
    telefone: string;
}

@Injectable({
    providedIn: 'root'
})
export class PedidoMesaService {
    private readonly http = inject(HttpClient);
    private readonly publicApiUrl = '/api/public/mesa';

    buscarMesa(token: string): Observable<Mesa> {
        return this.http.get<Mesa>(`${this.publicApiUrl}/${token}`);
    }

    buscarCardapio(token: string): Observable<CardapioPublico> {
        return this.http.get<CardapioPublico>(`${this.publicApiUrl}/${token}/cardapio`);
    }

    buscarClientePorTelefone(mesaToken: string, telefone: string): Observable<ClientePublico> {
        return this.http.get<ClientePublico>(`${this.publicApiUrl}/${mesaToken}/cliente/${telefone}`);
    }

    cadastrarCliente(mesaToken: string, request: CadastrarClienteRequest): Observable<ClientePublico> {
        return this.http.post<ClientePublico>(`${this.publicApiUrl}/${mesaToken}/cliente`, request);
    }

    criarPedido(request: CriarPedidoMesaRequest): Observable<PedidoMesaResponse> {
        return this.http.post<PedidoMesaResponse>(`${this.publicApiUrl}/pedido`, request);
    }
}
