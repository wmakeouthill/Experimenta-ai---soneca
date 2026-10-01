import { htmlCartoesMesa, qrCaminho } from './cartao-mesa.util';

describe('cartao-mesa.util', () => {
  const cartao = (numero: number, nome = 'Varanda') => ({
    numero,
    nome,
    url: `https://exemplo.app/pedido-mesa/${numero}-0b6f3c1e-8a2d-4f5b-9c7e-1d2a3b4c5d6e`,
  });

  it('gera uma folha A4 a cada 4 cartões', () => {
    const html = htmlCartoesMesa([1, 2, 3, 4, 5].map(n => cartao(n)));
    expect(html.match(/class="folha"/g)?.length).toBe(2);
  });

  it('escapa o nome da mesa', () => {
    const html = htmlCartoesMesa([cartao(7, '<img src=x onerror=alert(1)>')]);
    expect(html).not.toContain('<img src=x');
    expect(html).toContain('&#60;img src=x onerror=alert(1)&#62;');
  });

  it('gera QR com tamanho válido (4 × versão + 17 módulos)', () => {
    const { modulos, d } = qrCaminho(cartao(12).url);
    expect((modulos - 17) % 4).toBe(0);
    expect(d.startsWith('M0 0h7')).toBeTrue();
  });
});
