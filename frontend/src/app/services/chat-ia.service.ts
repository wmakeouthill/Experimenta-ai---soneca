import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, catchError, of } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ChatIARequest {
    message: string;
    sessionId?: string;
}

export interface ChatIAResponse {
    reply: string;
}

/**
 * Service para comunicação com o Chat IA backend.
 * Gerencia envio de mensagens e histórico via sessionId.
 */
@Injectable({
    providedIn: 'root'
})
export class ChatIAService {
    private readonly http = inject(HttpClient);
    private readonly apiUrl = `${environment.apiUrl}/chat-ia`;

    /**
     * Envia uma mensagem para o Chat IA e recebe a resposta.
     * 
     * @param mensagem texto da mensagem do usuário
     * @param sessionId identificador opcional da sessão para manter contexto
     * @returns Observable com a resposta do assistente
     */
    enviarMensagem(mensagem: string, sessionId?: string): Observable<ChatIAResponse> {
        const body: ChatIARequest = { message: mensagem };

        let headers = new HttpHeaders();
        if (sessionId) {
            headers = headers.set('X-Session-ID', sessionId);
        }

        return this.http.post<ChatIAResponse>(this.apiUrl, body, { headers }).pipe(
            catchError(error => {
                console.error('Erro ao enviar mensagem para Chat IA:', error);
                return of({ reply: 'Desculpe, ocorreu um erro. Tente novamente.' });
            })
        );
    }

    /**
     * Limpa o histórico de mensagens no backend para a sessão especificada.
     * 
     * @param sessionId identificador da sessão
     * @returns Observable<void>
     */
    limparHistorico(sessionId?: string): Observable<void> {
        let headers = new HttpHeaders();
        if (sessionId) {
            headers = headers.set('X-Session-ID', sessionId);
        }

        return this.http.post<void>(`${this.apiUrl}/clear`, {}, { headers }).pipe(
            catchError(error => {
                console.error('Erro ao limpar histórico:', error);
                return of(undefined);
            })
        );
    }
}
