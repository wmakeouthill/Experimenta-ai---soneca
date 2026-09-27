import { markdownChatParaHtml } from './markdown-chat';

describe('markdown do chat IA', () => {
  it('formata a lista de carnes que a IA mandou na VPS', () => {
    const resposta = [
      'Temos estas opções de carne:',
      '',
      '**Carne bovina tradicional:**',
      '- **N°1** — R$ 15,00',
      '- **N°2** — R$ 14,00',
    ].join('\n');

    expect(markdownChatParaHtml(resposta)).toBe(
      '<p>Temos estas opções de carne:</p>' +
      '<p><strong>Carne bovina tradicional:</strong></p>' +
      '<ul><li><strong>N°1</strong> — R$ 15,00</li><li><strong>N°2</strong> — R$ 14,00</li></ul>'
    );
  });

  it('mantém lista numerada com linha em branco entre itens e continua a numeração depois de texto', () => {
    expect(markdownChatParaHtml('1. a\n\n2. b\nobs\n3. c')).toBe(
      '<ol><li>a</li><li>b</li></ol><p>obs</p><ol start="3"><li>c</li></ol>'
    );
  });

  it('junta linhas seguidas no mesmo parágrafo e converte itálico e título', () => {
    expect(markdownChatParaHtml('## Dica\nlinha 1\nlinha *2*')).toBe(
      '<p><strong>Dica</strong><br>linha 1<br>linha <em>2</em></p>'
    );
  });

  it('não confunde multiplicação com itálico', () => {
    expect(markdownChatParaHtml('2 * 3 * 4')).toBe('<p>2 * 3 * 4</p>');
  });

  it('escapa HTML vindo da resposta', () => {
    expect(markdownChatParaHtml('<img src=x onerror="alert(1)"> & **ok**')).toBe(
      '<p>&lt;img src=x onerror=&quot;alert(1)&quot;&gt; &amp; <strong>ok</strong></p>'
    );
  });
});
