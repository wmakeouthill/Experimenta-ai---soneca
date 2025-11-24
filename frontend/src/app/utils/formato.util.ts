/**
 * Utilitários para formatação de valores.
 * Funções puras e reutilizáveis seguindo DRY.
 */
export class FormatoUtil {
  /**
   * Formata um valor numérico como moeda brasileira.
   */
  static moeda(valor: number | string | null | undefined): string {
    if (valor == null) return 'R$ 0,00';

    const num = typeof valor === 'string' ? parseFloat(valor) : valor;
    if (isNaN(num)) return 'R$ 0,00';

    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL'
    }).format(num);
  }

  /**
   * Remove formatação de moeda e retorna número.
   */
  static moedaParaNumero(valor: string): number {
    if (!valor) return 0;

    return parseFloat(
      valor
        .replace(/[^\d,]/g, '')
        .replace(',', '.')
    ) || 0;
  }

  /**
   * Formata número para input de moeda.
   */
  static numeroParaInputMoeda(valor: number | string | null | undefined): string {
    if (valor == null) return '';

    const num = typeof valor === 'string' ? parseFloat(valor) : valor;
    if (isNaN(num)) return '';

    return num.toFixed(2).replace('.', ',');
  }

  /**
   * Limita o texto às primeiras N palavras.
   */
  static limitarPalavras(texto: string | null | undefined, maxPalavras: number = 3): string {
    if (!texto) return '';
    const palavras = texto.trim().split(/\s+/);
    return palavras.slice(0, maxPalavras).join(' ');
  }

  /**
   * Formata data/hora para exibição em português brasileiro.
   */
  static dataHora(data: string | Date | null | undefined): string {
    if (!data) return '';

    const dataObj = typeof data === 'string' ? new Date(data) : data;
    if (isNaN(dataObj.getTime())) return '';

    return dataObj.toLocaleString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}

