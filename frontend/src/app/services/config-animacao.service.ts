import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LobbyReelItem } from '../components/lobby-pedidos/models/lobby-ui.types';

export interface ConfigAnimacao {
  animacaoAtivada: boolean;
  intervaloAnimacao: number;
  duracaoAnimacao: number;
  reels?: LobbyReelItem[];
  video1Url?: string | null;
  video2Url?: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class ConfigAnimacaoService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/config-animacao';

  carregar(): Observable<ConfigAnimacao> {
    return this.http.get<ConfigAnimacao>(this.apiUrl);
  }

  salvar(config: ConfigAnimacao): Observable<ConfigAnimacao> {
    return this.http.post<ConfigAnimacao>(this.apiUrl, config);
  }
}

