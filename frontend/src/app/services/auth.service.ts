import { Injectable, inject, signal, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Router } from '@angular/router';

export interface LoginRequest {
  emailOuUsuario: string;
  senha: string;
}

export interface LoginResponse {
  token: string;
  tipo: string;
  usuario: UsuarioDTO;
}

export interface UsuarioDTO {
  id: string;
  nome: string;
  email: string;
  role: string;
  descricaoRole: string;
  ativo: boolean;
}

export interface CriarUsuarioRequest {
  nome: string;
  email: string;
  senha: string;
  role: 'ADMINISTRADOR' | 'OPERADOR' | 'TOTEM';
}

export interface AtualizarUsuarioRequest {
  nome?: string;
  role?: 'ADMINISTRADOR' | 'OPERADOR' | 'TOTEM';
  ativo?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly apiUrl = '/api/auth';
  private readonly adminUrl = '/api/admin/usuarios';
  
  readonly usuarioAtual = signal<UsuarioDTO | null>(null);
  readonly estaAutenticado = signal<boolean>(false);
  readonly isAdministrador = signal<boolean>(false);
  readonly isOperador = signal<boolean>(false);
  readonly isTotem = signal<boolean>(false);

  private get isBrowser(): boolean {
    return isPlatformBrowser(this.platformId);
  }

  constructor() {
    // Carregar usuário do storage de forma segura (apenas no browser)
    if (this.isBrowser) {
      this.carregarUsuarioDoStorage();
    }
  }

  login(credenciais: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/login`, credenciais).pipe(
      tap(response => {
        this.salvarToken(response.token);
        this.salvarUsuario(response.usuario);
        this.atualizarEstado(response.usuario);
      })
    );
  }

  logout(): void {
    this.limparSessao();
    this.router.navigate(['/login']);
  }

  private limparSessao(): void {
    this.removerToken();
    this.removerUsuario();
    this.usuarioAtual.set(null);
    this.estaAutenticado.set(false);
    this.isAdministrador.set(false);
    this.isOperador.set(false);
    this.isTotem.set(false);
  }

  getToken(): string | null {
    if (!this.isBrowser) return null;
    return localStorage.getItem('token');
  }

  private salvarToken(token: string): void {
    if (!this.isBrowser) return;
    localStorage.setItem('token', token);
  }

  private removerToken(): void {
    if (!this.isBrowser) return;
    localStorage.removeItem('token');
  }

  private salvarUsuario(usuario: UsuarioDTO): void {
    if (!this.isBrowser) return;
    localStorage.setItem('usuario', JSON.stringify(usuario));
  }

  private removerUsuario(): void {
    if (!this.isBrowser) return;
    localStorage.removeItem('usuario');
  }

  private carregarUsuarioDoStorage(): void {
    if (!this.isBrowser) return;
    const token = this.getToken();
    const usuarioStr = localStorage.getItem('usuario');
    
    if (token && usuarioStr) {
      // Sem navegar: evita problemas de injeção durante a inicialização; os guards levam ao login
      if (AuthService.tokenExpirado(token)) {
        this.limparSessao();
        return;
      }
      try {
        const usuario = JSON.parse(usuarioStr) as UsuarioDTO;
        this.atualizarEstado(usuario);
      } catch (e) {
        this.limparSessao();
      }
    }
  }

  /** Lê o `exp` do JWT sem validar assinatura (quem valida é o backend). Ilegível = expirado. */
  static tokenExpirado(token: string): boolean {
    try {
      const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const { exp } = JSON.parse(atob(payload)) as { exp?: number };
      return typeof exp !== 'number' || exp * 1000 <= Date.now();
    } catch {
      return true;
    }
  }

  private atualizarEstado(usuario: UsuarioDTO): void {
    this.usuarioAtual.set(usuario);
    this.estaAutenticado.set(true);
    this.isAdministrador.set(usuario.role === 'ADMINISTRADOR');
    this.isOperador.set(usuario.role === 'OPERADOR');
    this.isTotem.set(usuario.role === 'TOTEM');
  }

  listarUsuarios(): Observable<UsuarioDTO[]> {
    return this.http.get<UsuarioDTO[]>(this.adminUrl);
  }

  buscarUsuarioPorId(id: string): Observable<UsuarioDTO> {
    return this.http.get<UsuarioDTO>(`${this.adminUrl}/${id}`);
  }

  criarUsuario(request: CriarUsuarioRequest): Observable<UsuarioDTO> {
    return this.http.post<UsuarioDTO>(this.adminUrl, request);
  }

  atualizarUsuario(id: string, request: AtualizarUsuarioRequest): Observable<UsuarioDTO> {
    return this.http.put<UsuarioDTO>(`${this.adminUrl}/${id}`, request);
  }

  excluirUsuario(id: string): Observable<void> {
    return this.http.delete<void>(`${this.adminUrl}/${id}`);
  }

  getRoleAtual(): string | null {
    return this.usuarioAtual()?.role ?? null;
  }

  temAcessoA(rolesPermitidos: string[]): boolean {
    const roleAtual = this.getRoleAtual();
    if (!roleAtual || !this.estaAutenticado()) {
      return false;
    }
    return rolesPermitidos.includes(roleAtual);
  }
}

