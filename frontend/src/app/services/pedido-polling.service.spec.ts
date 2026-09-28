import { TestBed, fakeAsync, tick, discardPeriodicTasks } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PedidoPollingService } from './pedido-polling.service';

describe('PedidoPollingService', () => {
  let service: PedidoPollingService;
  let http: HttpTestingController;

  const doSessao = (sessaoId: string) => http.expectOne(r => r.params.get('sessaoId') === sessaoId);

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PedidoPollingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('recarregar() sem argumento filtra pela sessão do polling ativo', fakeAsync(() => {
    service.iniciarPolling('s1');
    tick(0);
    doSessao('s1').flush([]);

    service.recarregar();
    doSessao('s1').flush([]);

    service.pararPolling();
    discardPeriodicTasks();
  }));

  it('iniciarPolling com outra sessão reinicia o polling com o novo filtro', fakeAsync(() => {
    service.iniciarPolling('s1');
    tick(0);
    doSessao('s1').flush([]);

    service.iniciarPolling('s2');
    tick(0);
    doSessao('s2').flush([]);

    tick(5000);
    doSessao('s2').flush([]);

    service.pararPolling();
    discardPeriodicTasks();
  }));
});
