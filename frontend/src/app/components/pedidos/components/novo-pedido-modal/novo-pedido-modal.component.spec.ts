import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Cliente } from '../../../../services/cliente.service';
import { MeioPagamento } from '../../../../services/pedido.service';
import { NovoPedidoModalComponent } from './novo-pedido-modal.component';

describe('NovoPedidoModalComponent', () => {
  it('pedido montado sobrevive a uma criação que falhou e só limpa ao fechar', () => {
    TestBed.configureTestingModule({
      imports: [NovoPedidoModalComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(NovoPedidoModalComponent);
    fixture.componentRef.setInput('produtos', []);
    fixture.componentRef.setInput('aberto', true);
    fixture.detectChanges();
    const modal = fixture.componentInstance;
    modal.clienteSelecionado.set({ id: 'c1', nome: 'Ana' } as Cliente);
    modal.itensSelecionados.set([{ produtoId: 'p1', quantidade: 2 }]);
    modal.meiosPagamento.set([{ meioPagamento: MeioPagamento.PIX, valor: 10 }]);

    modal.criarPedido();
    fixture.detectChanges();
    expect(modal.itensSelecionados().length).toBe(1);

    fixture.componentRef.setInput('aberto', false);
    fixture.detectChanges();
    expect(modal.itensSelecionados()).toEqual([]);
    expect(modal.clienteSelecionado()).toBeNull();
  });
});
