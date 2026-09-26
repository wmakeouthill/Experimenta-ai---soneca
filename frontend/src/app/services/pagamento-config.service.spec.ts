import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PagamentoConfigService, type ConfigPagamentoPublica } from './pagamento-config.service';

const URL_CONFIG = '/api/v1/pagamentos/config';

/** Espelho de ConfigPagamentoPublicaDTO.desativada(): master switch desligado no backend. */
const PAGAMENTOS_DESLIGADOS: ConfigPagamentoPublica = {
  pagamentosAtivos: false,
  totem: { pixAtivo: false, cartaoAtivo: false },
  mesa: { pixAtivo: false, cartaoAtivo: false, modo: 'PRE_PAGO' },
  gatewayPixSimulado: false,
};

describe('PagamentoConfigService.mesaPrePago', () => {
  let service: PagamentoConfigService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PagamentoConfigService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('com pagamentos desligados a mesa segue manual, mesmo com modo PRE_PAGO', () => {
    service.carregar();
    http.expectOne(URL_CONFIG).flush(PAGAMENTOS_DESLIGADOS);

    expect(service.mesaPrePago()).toBeFalse();
  });

  it('sem config (backend indisponivel) a mesa segue manual', () => {
    service.carregar();
    http.expectOne(URL_CONFIG).flush('erro', { status: 503, statusText: 'Service Unavailable' });

    expect(service.mesaPrePago()).toBeFalse();
  });

  it('PRE_PAGO com PIX digital ativo exige pagamento antes de enviar', () => {
    service.carregar();
    http.expectOne(URL_CONFIG).flush({
      ...PAGAMENTOS_DESLIGADOS,
      pagamentosAtivos: true,
      mesa: { pixAtivo: true, cartaoAtivo: false, modo: 'PRE_PAGO' },
    });

    expect(service.mesaPrePago()).toBeTrue();
  });
});
