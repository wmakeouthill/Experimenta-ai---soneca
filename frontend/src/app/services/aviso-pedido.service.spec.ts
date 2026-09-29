import { TestBed } from '@angular/core/testing';

import { AvisoPedidoService } from './aviso-pedido.service';
import { PedidoPendente } from './fila-pedidos-mesa.service';

const pendente = (id: string, numeroMesa: number) => ({ id, numeroMesa }) as PedidoPendente;
const frase = (p: PedidoPendente) => `Chegou um novo pedido da mesa ${p.numeroMesa}`;

describe('AvisoPedidoService', () => {
  let service: AvisoPedidoService;
  let falar: jasmine.Spy<(fala: SpeechSynthesisUtterance) => void>;

  beforeEach(() => {
    service = TestBed.inject(AvisoPedidoService);
    falar = spyOn(speechSynthesis, 'speak');
  });

  it('fala em pt-BR só o pedido que entrou na fila desde a última leitura', () => {
    service.anunciarNovos([pendente('p1', 3)], [pendente('p1', 3), pendente('p2', 5)], frase);

    expect(falar).toHaveBeenCalledTimes(1);
    const fala = falar.calls.mostRecent().args[0];
    expect(fala.text).toBe('Chegou um novo pedido da mesa 5');
    expect(fala.lang).toBe('pt-BR');
  });

  it('mostra a notificação do Windows quando a permissão foi concedida', () => {
    const notificar = spyOn(window, 'Notification');
    Object.defineProperty(window.Notification, 'permission', { value: 'granted' });

    service.anunciarNovos([], [pendente('p1', 7)], frase);

    expect(notificar).toHaveBeenCalledOnceWith('Novo pedido', { body: 'Chegou um novo pedido da mesa 7' });
  });
});
