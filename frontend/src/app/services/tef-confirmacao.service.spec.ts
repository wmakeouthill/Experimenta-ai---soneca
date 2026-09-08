import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import {
  TefConfirmacaoService,
  type ConfirmacaoTefPendente,
  type EstornoTefTotem,
  type ResultadoTefTotem,
} from './tef-confirmacao.service';

const URL_CONFIRMAR = '/api/v1/pagamentos/cartao-presencial/confirmar';

const APROVADO: ResultadoTefTotem = Object.freeze({
  sucesso: true,
  status: 'APROVADO',
  correlationId: 'corr-1',
  nsu: '37384',
  bandeira: 'MASTERCARD',
  autorizacao: '006299',
  adquirente: 'GETNET_AUTTAR',
  mensagem: 'TRANSAC APROVADA',
  comprovanteCliente: 'REDE GETNET\nCARTAO ************6479',
  dataTransacao: '220615',
  valorCentavos: 4579,
});

/** Dobra do bridge do Electron: registra o que o renderer mandou o processo principal fazer. */
function fakeTotemApi(pendentes: ConfirmacaoTefPendente[] = []) {
  const api = {
    estornos: [] as EstornoTefTotem[],
    baixadas: [] as string[],
    cancelarPagamentoTef: (payload: EstornoTefTotem) => {
      api.estornos.push(payload);
      return Promise.resolve({ sucesso: true, status: 'CANCELADO', correlationId: payload.correlationId });
    },
    confirmacoesTefPendentes: () => Promise.resolve(pendentes),
    marcarConfirmacaoTefRegistrada: (correlationId: string) => {
      api.baixadas.push(correlationId);
      return Promise.resolve();
    },
  };
  return api;
}

function pendencia(correlationId: string): ConfirmacaoTefPendente {
  return {
    confirmacao: { correlationId, aprovado: true, nsuTef: `nsu-${correlationId}` },
    estorno: { correlationId, nsu: `nsu-${correlationId}`, dataTransacao: '220615', valorCentavos: 4579 },
  };
}

describe('TefConfirmacaoService', () => {
  let servico: TefConfirmacaoService;
  let http: HttpTestingController;
  let totem: ReturnType<typeof fakeTotemApi>;

  function instalarTotem(pendentes: ConfirmacaoTefPendente[] = []) {
    totem = fakeTotemApi(pendentes);
    (window as unknown as Record<string, unknown>)['totemAPI'] = totem;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servico = TestBed.inject(TefConfirmacaoService);
    http = TestBed.inject(HttpTestingController);
    instalarTotem();
  });

  afterEach(() => {
    delete (window as unknown as Record<string, unknown>)['totemAPI'];
    http.verify();
  });

  // ===== Venda ao vivo =====

  it('envia a via do cliente ao backend, nunca a linha do visor da PinPad', async () => {
    const confirmando = servico.confirmarAprovacao(APROVADO);

    const req = http.expectOne(URL_CONFIRMAR);
    expect(req.request.body.comprovanteCliente).toBe('REDE GETNET\nCARTAO ************6479');
    expect(req.request.body.comprovanteCliente).not.toContain('TRANSAC APROVADA');
    expect(req.request.body).toEqual(
      jasmine.objectContaining({
        correlationId: 'corr-1',
        aprovado: true,
        nsuTef: '37384',
        bandeira: 'MASTERCARD',
        codigoAutorizacao: '006299',
        codigoAdquirente: 'GETNET_AUTTAR',
      })
    );

    req.flush({ correlationId: 'corr-1', status: 'APROVADO' });
    await confirmando;
  });

  it('sucesso baixa a pendencia do disco e nao estorna', async () => {
    const confirmando = servico.confirmarAprovacao(APROVADO);
    http.expectOne(URL_CONFIRMAR).flush({ correlationId: 'corr-1', status: 'APROVADO' });

    await confirmando;

    expect(totem.baixadas).toEqual(['corr-1']);
    expect(totem.estornos).toEqual([]);
  });

  // ===== O dinheiro ja foi capturado: cada ramo abaixo e uma venda em risco =====

  it('recusa definitiva (4xx) estorna pela 128 com nsu, data e valor', async () => {
    const confirmando = servico.confirmarAprovacao(APROVADO);
    http.expectOne(URL_CONFIRMAR).flush({ erro: 'pedido ja pago' }, { status: 409, statusText: 'Conflict' });

    await expectAsync(confirmando).toBeRejected();

    expect(totem.estornos).toEqual([
      { correlationId: 'corr-1', nsu: '37384', dataTransacao: '220615', valorCentavos: 4579 },
    ]);
    expect(totem.baixadas).toEqual(['corr-1']);
  });

  it('falha transitoria (5xx) nao estorna e mantem a pendencia para a proxima abertura', async () => {
    const confirmando = servico.confirmarAprovacao(APROVADO);
    http.expectOne(URL_CONFIRMAR).flush('indisponivel', { status: 503, statusText: 'Service Unavailable' });

    await expectAsync(confirmando).toBeRejected();

    expect(totem.estornos).toEqual([]);
    expect(totem.baixadas).toEqual([]);
  });

  it('backend fora do ar tambem mantem a pendencia', async () => {
    const confirmando = servico.confirmarAprovacao(APROVADO);
    http.expectOne(URL_CONFIRMAR).error(new ProgressEvent('erro de rede'));

    await expectAsync(confirmando).toBeRejected();

    expect(totem.estornos).toEqual([]);
    expect(totem.baixadas).toEqual([]);
  });

  it('sem nsu o estorno ainda sai, e o driver decide entre 128 e desfazimento', async () => {
    const confirmando = servico.confirmarAprovacao({ ...APROVADO, nsu: undefined, dataTransacao: undefined });
    http.expectOne(URL_CONFIRMAR).flush('recusado', { status: 400, statusText: 'Bad Request' });

    await expectAsync(confirmando).toBeRejected();

    expect(totem.estornos.length).toBe(1);
    expect(totem.estornos[0].nsu).toBeUndefined();
  });

  // ===== Dreno na abertura =====

  it('dreno reenvia todas as pendencias e baixa cada uma', async () => {
    instalarTotem([pendencia('corr-a'), pendencia('corr-b')]);

    const drenando = servico.drenarPendentes();
    // O dreno e sequencial: cada POST so sai depois que o anterior resolve.
    for (const id of ['corr-a', 'corr-b']) {
      const req = await aguardarRequisicao(http, URL_CONFIRMAR);
      expect(req.request.body.correlationId).toBe(id);
      req.flush({ correlationId: id, status: 'APROVADO' });
    }

    await drenando;
    expect(totem.baixadas).toEqual(['corr-a', 'corr-b']);
  });

  it('pendencia com 5xx nao impede a seguinte e continua no disco', async () => {
    instalarTotem([pendencia('corr-a'), pendencia('corr-b')]);

    const drenando = servico.drenarPendentes();
    (await aguardarRequisicao(http, URL_CONFIRMAR)).flush('fora', { status: 500, statusText: 'Server Error' });
    (await aguardarRequisicao(http, URL_CONFIRMAR)).flush({ correlationId: 'corr-b', status: 'APROVADO' });

    await drenando;
    expect(totem.baixadas).toEqual(['corr-b']);
    expect(totem.estornos).toEqual([]);
  });

  it('pendencia recusada em definitivo e estornada durante o dreno', async () => {
    instalarTotem([pendencia('corr-a')]);

    const drenando = servico.drenarPendentes();
    (await aguardarRequisicao(http, URL_CONFIRMAR)).flush('nao encontrado', {
      status: 404,
      statusText: 'Not Found',
    });

    await drenando;
    expect(totem.estornos).toEqual([
      { correlationId: 'corr-a', nsu: 'nsu-corr-a', dataTransacao: '220615', valorCentavos: 4579 },
    ]);
    expect(totem.baixadas).toEqual(['corr-a']);
  });

  it('sem totem (navegador comum) o dreno nao faz nada e nao quebra', async () => {
    delete (window as unknown as Record<string, unknown>)['totemAPI'];

    await servico.drenarPendentes();

    expect(servico.totemApi()).toBeNull();
    http.expectNone(URL_CONFIRMAR);
  });

  it('confirmacao fora do totem ainda grava no backend', async () => {
    delete (window as unknown as Record<string, unknown>)['totemAPI'];

    const confirmando = servico.confirmarAprovacao(APROVADO);
    http.expectOne(URL_CONFIRMAR).flush({ correlationId: 'corr-1', status: 'APROVADO' });

    await expectAsync(confirmando).toBeResolved();
  });
});

/**
 * O dreno so dispara o proximo POST depois que o anterior resolve, e o HttpTestingController
 * enxerga a requisicao apenas quando a microtask do await ja rodou.
 */
async function aguardarRequisicao(http: HttpTestingController, url: string) {
  for (let tentativa = 0; tentativa < 20; tentativa += 1) {
    const pendentes = http.match(url);
    if (pendentes.length) {
      return pendentes[0];
    }
    await Promise.resolve();
  }
  throw new Error(`Nenhuma requisicao para ${url}`);
}
