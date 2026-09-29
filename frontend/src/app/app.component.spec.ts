import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';
import { AuthService } from './services/auth.service';

describe('AppComponent', () => {
  const configurar = (perfil: 'OPERADOR' | 'TOTEM') => {
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            isAdministrador: signal(false),
            isOperador: signal(perfil === 'OPERADOR'),
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
    http.expectOne(r => r.url.includes('/api/sessoes-trabalho/ativa'));
  });

  it('totem não busca sessão (403 derrubava o login do quiosque)', () => {
    const http = configurar('TOTEM');
    http.expectNone(r => r.url.includes('/api/sessoes-trabalho'));
  });
});
