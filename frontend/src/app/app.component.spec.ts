import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AppComponent } from './app.component';
import { AuthService } from './services/auth.service';
import { PedidoPollingService } from './services/pedido-polling.service';

describe('AppComponent', () => {
  const estaAutenticado = signal(true);
  const isOperador = signal(false);

  const configurar = (perfil: 'OPERADOR' | 'TOTEM') => {
    estaAutenticado.set(true);
    isOperador.set(perfil === 'OPERADOR');
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', children: [] }]),
        {
          provide: AuthService,
          useValue: {
            estaAutenticado,
            isAdministrador: signal(false),
            isOperador,
            isTotem: signal(perfil === 'TOTEM'),
          },
        },
      ],
    });
    TestBed.createComponent(AppComponent).detectChanges();
    return TestBed.inject(HttpTestingController);
  };

  it('operador inicia os serviços globais buscando a sessão ativa', () => {
    const http = configurar('OPERADOR');
    expect(http.match(r => r.url.includes('/api/sessoes-trabalho/ativa')).length).toBe(1);
  });

  it('totem não busca sessão (403 derrubava o login do quiosque)', () => {
    const http = configurar('TOTEM');
    expect(http.match(r => r.url.includes('/api/sessoes-trabalho'))).toEqual([]);
  });

  it('logout para o polling (senão segue batendo 401 no login e volta com a sessão velha)', async () => {
    configurar('OPERADOR');
    const polling = TestBed.inject(PedidoPollingService);
    spyOn(polling, 'pararPolling');
    estaAutenticado.set(false);
    isOperador.set(false);

    await TestBed.inject(Router).navigateByUrl('/login');

    expect(polling.pararPolling).toHaveBeenCalled();
  });
});
