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

  it('sem sessão não lista pedidos (evita baixar o histórico inteiro)', fakeAsync(() => {
    service.iniciarPolling('s1');
    tick(0);
    doSessao('s1').flush([{ id: 'p1' }]);

    service.iniciarPolling(undefined);
    service.recarregar();
    tick(10000);

    http.expectNone(() => true);
    expect(service.pedidos()).toEqual([]);
    expect(service.pollingAtivo()).toBeFalse();
  }));

  it('sessão aberta vazia: o primeiro pedido é notificado', fakeAsync(() => {
    const notificados: string[] = [];
    service.onNovoPedido.subscribe(p => notificados.push(p.id));
    service.iniciarPolling('s1');
    tick(0);
    doSessao('s1').flush([]);

    tick(5000);
    doSessao('s1').flush([{ id: 'p1', dataPedido: new Date().toISOString() }]);

    expect(notificados).toEqual(['p1']);
    service.pararPolling();
    discardPeriodicTasks();
  }));

  it('erro no polling mantém a última lista até a próxima resposta', fakeAsync(() => {
    service.iniciarPolling('s1');
    tick(0);
    doSessao('s1').flush([{ id: 'p1' }]);

    tick(5000);
    doSessao('s1').flush(null, { status: 500, statusText: 'Erro' });
    expect(service.pedidos().map(p => p.id)).toEqual(['p1']);
    expect(service.erro()).not.toBeNull();

    tick(5000);
    doSessao('s1').flush([{ id: 'p1' }, { id: 'p2' }]);
    expect(service.pedidos()).toHaveSize(2);
    expect(service.erro()).toBeNull();

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
