import { NomeIcone } from '../components/shared/icone/icone.component';

export type Role = 'ADMINISTRADOR' | 'OPERADOR';

export interface Modulo {
  id: string;
  nome: string;
  descricao: string;
  icone: NomeIcone;
  rota: string;
  disponivel: boolean;
  rolesPermitidos: Role[];
  bloqueado: boolean;
}
