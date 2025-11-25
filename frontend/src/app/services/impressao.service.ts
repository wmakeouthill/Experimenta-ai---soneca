import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export enum TipoImpressora {
  EPSON_TM_T20 = 'EPSON_TM_T20',
  DARUMA_800 = 'DARUMA_800',
  GENERICA_ESCPOS = 'GENERICA_ESCPOS'
}

export interface ImprimirCupomRequest {
  pedidoId: string;
  tipoImpressora: TipoImpressora;
  nomeImpressora?: string;
  nomeEstabelecimento?: string;
  enderecoEstabelecimento?: string;
  telefoneEstabelecimento?: string;
  cnpjEstabelecimento?: string;
}

export interface ImprimirCupomResponse {
  sucesso: boolean;
  mensagem: string;
  dataImpressao: string;
  pedidoId: string;
}

export interface ConfiguracaoImpressoraDTO {
  id?: string;
  tipoImpressora: TipoImpressora;
  nomeEstabelecimento: string;
  enderecoEstabelecimento?: string;
  telefoneEstabelecimento?: string;
  cnpjEstabelecimento?: string;
  logoBase64?: string;
  ativa?: boolean;
}

export interface SalvarConfiguracaoImpressoraRequest {
  tipoImpressora: TipoImpressora;
  nomeEstabelecimento: string;
  enderecoEstabelecimento?: string;
  telefoneEstabelecimento?: string;
  cnpjEstabelecimento?: string;
  logoBase64?: string;
}

@Injectable({
  providedIn: 'root'
})
export class ImpressaoService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/impressao';

  imprimirCupom(request: ImprimirCupomRequest): Observable<ImprimirCupomResponse> {
    return this.http.post<ImprimirCupomResponse>(`${this.apiUrl}/cupom-fiscal`, request);
  }

  imprimirCupomTeste(configuracao: {
    tipoImpressora: TipoImpressora;
    nomeEstabelecimento: string;
    enderecoEstabelecimento?: string;
    telefoneEstabelecimento?: string;
    cnpjEstabelecimento?: string;
  }): Observable<ImprimirCupomResponse> {
    const request: ImprimirCupomRequest = {
      pedidoId: 'teste', // ID especial para teste
      tipoImpressora: configuracao.tipoImpressora,
      nomeEstabelecimento: configuracao.nomeEstabelecimento,
      enderecoEstabelecimento: configuracao.enderecoEstabelecimento,
      telefoneEstabelecimento: configuracao.telefoneEstabelecimento,
      cnpjEstabelecimento: configuracao.cnpjEstabelecimento
    };
    
    return this.imprimirCupom(request);
  }

  buscarConfiguracao(): Observable<ConfiguracaoImpressoraDTO> {
    return this.http.get<ConfiguracaoImpressoraDTO>(`${this.apiUrl}/configuracao`);
  }

  salvarConfiguracao(request: SalvarConfiguracaoImpressoraRequest): Observable<ConfiguracaoImpressoraDTO> {
    return this.http.post<ConfiguracaoImpressoraDTO>(`${this.apiUrl}/configuracao`, request);
  }
}

