import { TestBed } from '@angular/core/testing';
import { throwError } from 'rxjs';
import { CategoriaService } from '../../../services/categoria.service';
import { ProdutoService } from '../../../services/produto.service';
import { useAutoAtendimentoCardapio } from './use-autoatendimento-cardapio';

describe('useAutoAtendimentoCardapio', () => {
  it('falha ao carregar propaga para a tela de erro e libera o carregando', async () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: CategoriaService, useValue: { listar: () => throwError(() => new Error('offline')) } },
        { provide: ProdutoService, useValue: { listar: () => throwError(() => new Error('offline')) } },
      ],
    });
    const cardapio = TestBed.runInInjectionContext(() => useAutoAtendimentoCardapio());

    await expectAsync(cardapio.carregar()).toBeRejected();
    expect(cardapio.carregando()).toBeFalse();
  });
});
