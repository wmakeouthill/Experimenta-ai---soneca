import { TestBed } from '@angular/core/testing';
import { Produto } from '../../../services/produto.service';
import { useCarrinho } from './use-carrinho';

describe('useCarrinho', () => {
  const xBurger = { id: 'p1', nome: 'X-Burger', preco: 18.9 } as Produto;

  beforeEach(() => sessionStorage.clear());

  const criar = () => TestBed.runInInjectionContext(() => useCarrinho());

  it('mesmo produto com a mesma personalização soma na linha existente', () => {
    const carrinho = criar();

    carrinho.abrirDetalhes(xBurger);
    carrinho.setObservacao('sem cebola');
    carrinho.adicionarAoCarrinho();
    carrinho.abrirDetalhes(xBurger);
    carrinho.setObservacao('sem cebola ');
    carrinho.adicionarAoCarrinho();

    expect(carrinho.itens().length).toBe(1);
    expect(carrinho.itens()[0].quantidade).toBe(2);
  });

  it('personalização diferente vira outra linha e +/- age só nela', () => {
    const carrinho = criar();

    carrinho.adicionarRapido(xBurger);
    carrinho.abrirDetalhes(xBurger);
    carrinho.setObservacao('sem cebola');
    carrinho.adicionarAoCarrinho();
    carrinho.alterarQuantidade(1, 1);

    expect(carrinho.itens().map(i => [i.observacao, i.quantidade])).toEqual([['', 1], ['sem cebola', 2]]);

    carrinho.alterarQuantidade(0, -1);
    expect(carrinho.itens().map(i => i.observacao)).toEqual(['sem cebola']);
  });
});
