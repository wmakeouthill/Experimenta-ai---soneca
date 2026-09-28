import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';

import { FilaPedidosMesaService, PedidoPendente } from './fila-pedidos-mesa.service';

const URL_FILA = '/api/pedidos/fila-mesa';

describe('FilaPedidosMesaService.iniciarPolling', () => {
  let service: FilaPedidosMesaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FilaPedidosMesaService);
    http = TestBed.inject(HttpTestingController);
  });

  it('continua consultando e mantém a fila depois de uma falha do backend', fakeAsync(() => {
    const pedido = { id: 'p1' } as PedidoPendente;
    const sub = service.iniciarPolling(1000).subscribe();

    http.expectOne(URL_FILA).flush([pedido]);
    expect(service.pedidosPendentes()).toEqual([pedido]);

    tick(1000);
    http.expectOne(URL_FILA).flush(null, { status: 503, statusText: 'Service Unavailable' });
    expect(service.pedidosPendentes()).toEqual([pedido]);

    tick(1000);
    http.expectOne(URL_FILA).flush([]);
    expect(service.pedidosPendentes()).toEqual([]);

    sub.unsubscribe();
    discardPeriodicTasks();
    http.verify();
  }));
});
