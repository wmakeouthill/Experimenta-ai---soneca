import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ImpressaoTotemService } from './impressao-totem.service';

const URL_CONFIG = '/api/impressao/configuracao';
const URL_FORMATAR = '/api/impressao/cupom-fiscal/formatar';

const CONFIG = Object.freeze({
  tipoImpressora: 'POS_80',
  devicePath: 'USB001',
  nomeEstabelecimento: 'Experimenta ai',
});

const FORMATADO = Object.freeze({
  sucesso: true,
  mensagem: 'ok',
  dadosEscPosBase64: 'Q1VQT00=',
  tipoImpressora: 'POS_80',
  pedidoId: 'ped-1',
});

interface PayloadImpressao {
  dadosBase64: string;
  tipoImpressora?: string;
  devicePath?: string;
}

/** Dobra da ponte IPC: registra o que o renderer mandou o processo principal imprimir. */
function fakeTotemApi(resposta = { sucesso: true }) {
  const api = {
    impressoes: [] as PayloadImpressao[],
    imprimir: (payload: PayloadImpressao) => {
      api.impressoes.push(payload);
      return Promise.resolve(resposta);
    },
  };
  return api;
}

describe('ImpressaoTotemService', () => {
  let servico: ImpressaoTotemService;
  let http: HttpTestingController;
  let totem: ReturnType<typeof fakeTotemApi>;

  function instalarTotem(resposta = { sucesso: true }) {
    totem = fakeTotemApi(resposta);
    (window as unknown as Record<string, unknown>)['totemAPI'] = totem;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servico = TestBed.inject(ImpressaoTotemService);
    http = TestBed.inject(HttpTestingController);
    instalarTotem();
  });

  afterEach(() => {
    delete (window as unknown as Record<string, unknown>)['totemAPI'];
    http.verify();
  });

  it('imprime o cupom com os dados formatados pelo backend e a impressora do banco', async () => {
    const imprimindo = servico.imprimirPedido('ped-1');

    http.expectOne(URL_CONFIG).flush(CONFIG);
    const formatar = await aguardarRequisicao(http, URL_FORMATAR);
    expect(formatar.request.body.pedidoId).toBe('ped-1');
    formatar.flush(FORMATADO);

    expect(await imprimindo).toEqual({ sucesso: true });
    expect(totem.impressoes).toEqual([
      { dadosBase64: 'Q1VQT00=', tipoImpressora: 'POS_80', devicePath: 'USB001' },
    ]);
  });

  // ===== A venda ja esta paga e gravada: nenhum ramo abaixo pode lancar =====

  it('sem impressora no totem nem chega a chamar o backend', async () => {
    delete (window as unknown as Record<string, unknown>)['totemAPI'];

    const resultado = await servico.imprimirPedido('ped-1');

    expect(resultado.sucesso).toBeFalse();
    http.expectNone(URL_CONFIG);
  });

  it('backend fora do ar nao lanca, so devolve falha', async () => {
    const imprimindo = servico.imprimirPedido('ped-1');
    http.expectOne(URL_CONFIG).flush('fora', { status: 500, statusText: 'Server Error' });

    await expectAsync(imprimindo).toBeResolvedTo(
      jasmine.objectContaining({ sucesso: false })
    );
    expect(totem.impressoes).toEqual([]);
  });

  it('cupom que o backend nao conseguiu formatar nao vai para o papel', async () => {
    const imprimindo = servico.imprimirPedido('ped-1');
    http.expectOne(URL_CONFIG).flush(CONFIG);
    (await aguardarRequisicao(http, URL_FORMATAR)).flush({
      ...FORMATADO,
      sucesso: false,
      mensagem: 'pedido sem itens',
    });

    expect(await imprimindo).toEqual({ sucesso: false, mensagem: 'pedido sem itens' });
    expect(totem.impressoes).toEqual([]);
  });

  it('impressora sem papel devolve falha em vez de estourar', async () => {
    instalarTotem({ sucesso: false });

    const imprimindo = servico.imprimirPedido('ped-1');
    http.expectOne(URL_CONFIG).flush(CONFIG);
    (await aguardarRequisicao(http, URL_FORMATAR)).flush(FORMATADO);

    expect((await imprimindo).sucesso).toBeFalse();
  });

  // ===== Via do cliente =====

  it('via do cliente vai como texto, sem passar pelo formatador do backend', async () => {
    const imprimindo = servico.imprimirComprovanteTef('REDE GETNET\nCARTAO ************6479');
    http.expectOne(URL_CONFIG).flush(CONFIG);

    expect(await imprimindo).toEqual({ sucesso: true });
    http.expectNone(URL_FORMATAR);
    expect(atob(totem.impressoes[0].dadosBase64)).toBe('REDE GETNET\nCARTAO ************6479');
  });

  /** btoa so aceita latin1: acento sem tratamento derrubaria a impressao do comprovante. */
  it('acento no comprovante nao quebra o base64', async () => {
    const imprimindo = servico.imprimirComprovanteTef('TRANSACAO APROVADA - CREDITO A VISTA\nJOAO ANDRE');
    http.expectOne(URL_CONFIG).flush(CONFIG);
    await imprimindo;

    const comAcento = servico.imprimirComprovanteTef('CARTAO DE CREDITO — SAO JOAO, AVENIDA JOSÉ');
    http.expectOne(URL_CONFIG).flush(CONFIG);

    expect((await comAcento).sucesso).toBeTrue();
    expect(atob(totem.impressoes[1].dadosBase64)).toContain('JOSE');
  });

  it('comprovante vazio nao chama nada', async () => {
    const resultado = await servico.imprimirComprovanteTef('   ');

    expect(resultado.sucesso).toBeFalse();
    http.expectNone(URL_CONFIG);
  });
});

/** O servico encadeia awaits: a proxima requisicao so aparece depois da microtask anterior. */
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
