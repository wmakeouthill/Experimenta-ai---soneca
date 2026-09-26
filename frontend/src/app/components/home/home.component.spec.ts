import { saudacaoPorHora } from './home.component';

describe('saudacaoPorHora', () => {
  it('troca de saudação nas viradas de período', () => {
    expect(saudacaoPorHora(4)).toBe('Boa noite');
    expect(saudacaoPorHora(5)).toBe('Bom dia');
    expect(saudacaoPorHora(11)).toBe('Bom dia');
    expect(saudacaoPorHora(12)).toBe('Boa tarde');
    expect(saudacaoPorHora(17)).toBe('Boa tarde');
    expect(saudacaoPorHora(18)).toBe('Boa noite');
  });
});
