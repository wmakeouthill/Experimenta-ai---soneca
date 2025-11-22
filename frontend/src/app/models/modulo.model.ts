export interface Modulo {
  id: string;
  nome: string;
  descricao: string;
  icone: string;
  rota: string;
  cor: 'primary' | 'success' | 'warning' | 'info';
  disponivel: boolean;
}

