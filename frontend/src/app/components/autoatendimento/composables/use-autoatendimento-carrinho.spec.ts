import { TestBed } from '@angular/core/testing';
import { Adicional } from '../../../services/adicional.service';
import { Produto } from '../../../services/produto.service';
import { useAutoAtendimentoCarrinho } from './use-autoatendimento-carrinho';

describe('useAutoAtendimentoCarrinho', () => {
  const xBurger = { id: 'p1', nome: 'X-Burger', preco: 18.9 } as Produto;
  const bacon = { id: 'a1', nome: 'Bacon', preco: 4 } as Adicional;

  beforeEach(() => sessionStorage.clear());

  const criar = () => TestBed.runInInjectionContext(() => useAutoAtendimentoCarrinho());

  it('adição rápida repetida soma em vez de manter 1', () => {
    const carrinho = criar();

    carrinho.adicionarRapido(xBurger);
    carrinho.adicionarRapido(xBurger);

    expect(carrinho.itens().length).toBe(1);
    expect(carrinho.itens()[0].quantidade).toBe(2);
  });

  it('modal abre limpo e personalização diferente vira outra linha; +/- e remover agem só nela', () => {
    const carrinho = criar();
    carrinho.adicionarRapido(xBurger);

    carrinho.abrirDetalhes(xBurger);
    expect(carrinho.quantidadeTemp()).toBe(1);
    carrinho.adicionarAdicional(bacon);
    carrinho.confirmarProduto();
    carrinho.atualizarQuantidadeItem(1, 3);

    expect(carrinho.itens().map(i => [i.adicionais.length, i.quantidade])).toEqual([[0, 1], [1, 3]]);
    expect(carrinho.totalValor()).toBeCloseTo(18.9 + (18.9 + 4) * 3);

    carrinho.removerItem(0);
    expect(carrinho.itens().map(i => i.adicionais.length)).toEqual([1]);
  });
});
