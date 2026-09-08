import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { firstValueFrom } from 'rxjs';

import {
  type ConfirmarPagamentoCartaoRequest,
  type MeioPagamentoGateway,
  type PagamentoDTO,
  PagamentoService,
} from './pagamento.service';

export interface ResultadoTefTotem {
  sucesso: boolean;
  status: string;
  correlationId: string;
  nsu?: string;
  bandeira?: string;
  autorizacao?: string;
  adquirente?: string;
  /** Linha do visor da PinPad. Nao e comprovante. */
  mensagem?: string;
  /** Via do cliente montada pelo CTF; a adquirente exige entrega-la ao portador. */
  comprovanteCliente?: string;
  dataTransacao?: string;
  valorCentavos?: number;
}

/** Dados da operacao 128 — o unico caminho de volta depois que o dinheiro foi capturado. */
export interface EstornoTefTotem {
  correlationId: string;
  nsu?: string;
  dataTransacao?: string;
  valorCentavos?: number;
}

export interface ConfirmacaoTefPendente {
  confirmacao: ConfirmarPagamentoCartaoRequest;
  estorno: EstornoTefTotem;
}

export interface TotemApi {
  iniciarPagamentoTef?: (payload: {
    correlationId: string;
    valorCentavos: number;
    meio: MeioPagamentoGateway;
  }) => Promise<ResultadoTefTotem>;
  cancelarPagamentoTef?: (payload: EstornoTefTotem) => Promise<ResultadoTefTotem>;
  confirmacoesTefPendentes?: () => Promise<ConfirmacaoTefPendente[]>;
  marcarConfirmacaoTefRegistrada?: (correlationId: string) => Promise<void>;
}

/**
 * Caminho de dinheiro do totem: da aprovacao na maquininha ate o registro no backend.
 *
 * Quando o CTF aprova, o dinheiro ja saiu do cartao e o backend ainda nao sabe de nada. O
 * processo principal guarda a confirmacao em disco; este servico e quem decide o destino dela:
 * registrar, deixar pendente ou estornar. Vive fora do componente justamente para ter teste
 * proprio — cada ramo aqui e uma venda que pode sumir ou ser cobrada duas vezes.
 */
@Injectable({ providedIn: 'root' })
export class TefConfirmacaoService {
  private readonly pagamentoService = inject(PagamentoService);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  totemApi(): TotemApi | null {
    if (!this.isBrowser) {
      return null;
    }
    return (window as Window & { totemAPI?: TotemApi }).totemAPI ?? null;
  }

  /** Venda ao vivo: monta a confirmacao e o estorno a partir do que o CTF devolveu. */
  confirmarAprovacao(resultado: ResultadoTefTotem): Promise<PagamentoDTO> {
    return this.confirmarComEstorno(
      {
        correlationId: resultado.correlationId,
        aprovado: true,
        nsuTef: resultado.nsu,
        bandeira: resultado.bandeira,
        codigoAutorizacao: resultado.autorizacao,
        codigoAdquirente: resultado.adquirente,
        comprovanteCliente: resultado.comprovanteCliente,
      },
      {
        correlationId: resultado.correlationId,
        nsu: resultado.nsu,
        dataTransacao: resultado.dataTransacao,
        valorCentavos: resultado.valorCentavos,
      }
    );
  }

  /**
   * Se o totem caiu entre a aprovacao na maquininha e a gravacao no backend, a venda ficou
   * capturada sem registro. O processo principal guardou o arquivo; aqui reenviamos na
   * abertura. O backend e idempotente por correlationId, entao reenvio nao cobra de novo.
   */
  async drenarPendentes(): Promise<void> {
    const pendentes = (await this.totemApi()?.confirmacoesTefPendentes?.()) ?? [];

    for (const pendente of pendentes) {
      try {
        await this.confirmarComEstorno(pendente.confirmacao, pendente.estorno);
      } catch {
        // Falha transitoria continua pendente para a proxima abertura; recusa definitiva ja
        // foi estornada e baixada dentro do confirmarComEstorno.
      }
    }
  }

  /**
   * Grava a confirmacao no backend. Nesse ponto o dinheiro ja foi capturado, entao recusa
   * definitiva (4xx) tem um unico caminho de volta: estorno pela operacao 128. Falha
   * transitoria (5xx / rede) mantem a pendencia em disco para a proxima abertura.
   */
  private async confirmarComEstorno(
    confirmacao: ConfirmarPagamentoCartaoRequest,
    estorno: EstornoTefTotem
  ): Promise<PagamentoDTO> {
    const totemApi = this.totemApi();

    try {
      const pagamento = await firstValueFrom(this.pagamentoService.confirmarCartao(confirmacao));
      await totemApi?.marcarConfirmacaoTefRegistrada?.(confirmacao.correlationId);
      return pagamento;
    } catch (erro) {
      if (erro instanceof HttpErrorResponse && erro.status >= 400 && erro.status < 500) {
        await totemApi?.cancelarPagamentoTef?.(estorno);
        await totemApi?.marcarConfirmacaoTefRegistrada?.(confirmacao.correlationId);
      }
      throw erro;
    }
  }
}
