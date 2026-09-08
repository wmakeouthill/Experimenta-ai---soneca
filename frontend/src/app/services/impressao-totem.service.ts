import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  type ConfiguracaoImpressoraDTO,
  type FormatarCupomResponse,
} from './impressao.service';
import { TefConfirmacaoService } from './tef-confirmacao.service';

export interface ResultadoImpressaoTotem {
  sucesso: boolean;
  mensagem?: string;
}

/**
 * Impressao do totem: mesmo esquema do Electron da lanchonete (backend formata o ESC/POS,
 * o processo principal fala com a impressora), so que por IPC em vez de servidor HTTP local.
 *
 * Regra que vale para tudo aqui: impressao NAO e caminho de dinheiro. Quando um metodo deste
 * servico roda, a venda ja foi aprovada pela adquirente e gravada no backend. Por isso nenhum
 * metodo lanca — impressora sem papel, sem cabo ou sem configuracao vira
 * `{ sucesso: false }` e o pedido continua pago.
 */
@Injectable({ providedIn: 'root' })
export class ImpressaoTotemService {
  private readonly http = inject(HttpClient);
  private readonly tefConfirmacao = inject(TefConfirmacaoService);
  private readonly apiUrl = '/api/impressao';

  /** Cupom do pedido (nota + itens), formatado pelo backend a partir do PedidoDTO. */
  async imprimirPedido(pedidoId: string): Promise<ResultadoImpressaoTotem> {
    const bridge = this.bridge();
    if (!bridge) {
      return { sucesso: false, mensagem: 'Totem sem impressora conectada.' };
    }

    try {
      const config = await firstValueFrom(
        this.http.get<ConfiguracaoImpressoraDTO>(`${this.apiUrl}/configuracao`)
      );
      const formatado = await firstValueFrom(
        this.http.post<FormatarCupomResponse>(`${this.apiUrl}/cupom-fiscal/formatar`, {
          pedidoId,
          tipoImpressora: config?.tipoImpressora,
          devicePath: config?.devicePath,
        })
      );

      if (!formatado?.sucesso || !formatado.dadosEscPosBase64) {
        return { sucesso: false, mensagem: formatado?.mensagem || 'Falha ao formatar o cupom.' };
      }

      // ponytail: logo fica de fora — e o unico pedaco que exige node-thermal-printer e jimp
      // no Electron. Adicionar quando a loja pedir logo no cupom do totem.
      return await bridge({
        dadosBase64: formatado.dadosEscPosBase64,
        tipoImpressora: formatado.tipoImpressora ?? config?.tipoImpressora,
        devicePath: config?.devicePath,
      });
    } catch (erro) {
      console.error('Falha ao imprimir o cupom do totem:', erro);
      return { sucesso: false, mensagem: 'Falha ao imprimir o cupom.' };
    }
  }

  /**
   * Via do cliente do TEF. Nao passa pelo backend: o texto ja vem montado pelo CTF e o
   * conversor do Electron aceita conteudo cru.
   */
  async imprimirComprovanteTef(comprovante: string): Promise<ResultadoImpressaoTotem> {
    const bridge = this.bridge();
    if (!bridge) {
      return { sucesso: false, mensagem: 'Totem sem impressora conectada.' };
    }
    if (!comprovante?.trim()) {
      return { sucesso: false, mensagem: 'Comprovante vazio.' };
    }

    try {
      const config = await firstValueFrom(
        this.http.get<ConfiguracaoImpressoraDTO>(`${this.apiUrl}/configuracao`)
      );
      return await bridge({
        dadosBase64: paraBase64(comprovante),
        tipoImpressora: config?.tipoImpressora,
        devicePath: config?.devicePath,
      });
    } catch (erro) {
      console.error('Falha ao imprimir a via do cliente:', erro);
      return { sucesso: false, mensagem: 'Falha ao imprimir a via do cliente.' };
    }
  }

  private bridge() {
    return this.tefConfirmacao.totemApi()?.imprimir ?? null;
  }
}

/**
 * ponytail: a impressora trabalha em CP850 e o btoa so aceita latin1, entao acentos viram a
 * letra sem acento em vez de byte invalido no papel. Comprovante de TEF e ASCII na pratica.
 * Se algum dia precisar de acento de verdade, codificar CP850 no processo principal.
 */
function paraBase64(texto: string): string {
  const semAcento = texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  return btoa(semAcento.replace(/[^\x00-\xFF]/g, '?'));
}
