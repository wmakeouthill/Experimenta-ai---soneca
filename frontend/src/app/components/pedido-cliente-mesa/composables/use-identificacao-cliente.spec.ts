import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ClienteAuthService } from '../../../services/cliente-auth.service';
import { PedidoMesaService } from '../../../services/pedido-mesa.service';
import { useIdentificacaoCliente } from './use-identificacao-cliente';

describe('identificação do cliente da mesa', () => {
  const cliente = { id: 'cliente-1', nome: 'Ana', telefone: '21987654321' };
  let pedidoMesaService: jasmine.SpyObj<PedidoMesaService>;
  let clienteAuthService: jasmine.SpyObj<ClienteAuthService>;

  beforeEach(() => {
    pedidoMesaService = jasmine.createSpyObj<PedidoMesaService>('PedidoMesaService', [
      'buscarClientePorTelefone',
      'cadastrarCliente',
    ]);
    clienteAuthService = jasmine.createSpyObj<ClienteAuthService>('ClienteAuthService', [
      'login',
      'logout',
    ]);
    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: 'server' },
        { provide: PedidoMesaService, useValue: pedidoMesaService },
        { provide: ClienteAuthService, useValue: clienteAuthService },
      ],
    });
  });

  it('pede senha antes de identificar um telefone já cadastrado', () => {
    pedidoMesaService.buscarClientePorTelefone.and.returnValue(of(cliente));
    const identificacao = TestBed.runInInjectionContext(() => useIdentificacaoCliente(() => 'mesa'));

    identificacao.setTelefone(cliente.telefone);
    identificacao.buscarCliente();

    expect(identificacao.etapa()).toBe('senha');
    expect(identificacao.clienteIdentificado()).toBeNull();
  });

  it('encaminha telefone desconhecido para cadastro', () => {
    pedidoMesaService.buscarClientePorTelefone.and.returnValue(
      throwError(() => ({ status: 404 }))
    );
    const identificacao = TestBed.runInInjectionContext(() => useIdentificacaoCliente(() => 'mesa'));

    identificacao.setTelefone('21987654321');
    identificacao.buscarCliente();

    expect(identificacao.etapa()).toBe('cadastro');
    expect(identificacao.clienteIdentificado()).toBeNull();
  });

  it('só identifica cliente existente depois de validar a senha', () => {
    pedidoMesaService.buscarClientePorTelefone.and.returnValue(of(cliente));
    clienteAuthService.login.and.returnValues(
      throwError(() => ({ status: 400 })),
      of({
        token: 'token',
        tipo: 'Bearer',
        cliente: { ...cliente, googleVinculado: false, temSenha: true },
      })
    );
    const identificacao = TestBed.runInInjectionContext(() => useIdentificacaoCliente(() => 'mesa'));
    const onSucesso = jasmine.createSpy('onSucesso');

    identificacao.setTelefone(cliente.telefone);
    identificacao.buscarCliente();
    identificacao.setSenha('incorreta');
    identificacao.entrarComSenha(onSucesso);
    expect(identificacao.clienteIdentificado()).toBeNull();

    identificacao.setSenha('correta');
    identificacao.entrarComSenha(onSucesso);
    expect(identificacao.clienteIdentificado()?.id).toBe(cliente.id);
    expect(onSucesso).toHaveBeenCalledTimes(1);
  });

  it('orienta conta antiga sem senha a usar Google ou atendimento', () => {
    pedidoMesaService.buscarClientePorTelefone.and.returnValue(of(cliente));
    clienteAuthService.login.and.returnValue(throwError(() => ({
      status: 400,
      error: { message: 'Cliente não possui senha definida. Por favor, cadastre uma senha.' },
    })));
    const identificacao = TestBed.runInInjectionContext(() => useIdentificacaoCliente(() => 'mesa'));

    identificacao.setTelefone(cliente.telefone);
    identificacao.buscarCliente();
    identificacao.setSenha('qualquer');
    identificacao.entrarComSenha(() => fail('conta sem senha não pode entrar'));

    expect(identificacao.erro()).toContain('Google ou peça ajuda no atendimento');
    expect(identificacao.clienteIdentificado()).toBeNull();
  });

  it('cadastra com senha e só libera o pedido após o login', () => {
    pedidoMesaService.buscarClientePorTelefone.and.returnValue(
      throwError(() => ({ status: 404 }))
    );
    pedidoMesaService.cadastrarCliente.and.returnValue(of(cliente));
    clienteAuthService.login.and.returnValue(of({
      token: 'token',
      tipo: 'Bearer',
      cliente: { ...cliente, googleVinculado: false, temSenha: true },
    }));
    const identificacao = TestBed.runInInjectionContext(() => useIdentificacaoCliente(() => 'mesa'));
    const onSucesso = jasmine.createSpy('onSucesso');

    identificacao.setTelefone(cliente.telefone);
    identificacao.buscarCliente();
    identificacao.setNome(cliente.nome);
    identificacao.setSenha('senha123');
    identificacao.setConfirmarSenha('senha123');
    identificacao.cadastrarCliente(onSucesso);

    expect(pedidoMesaService.cadastrarCliente).toHaveBeenCalledWith('mesa', {
      nome: cliente.nome,
      telefone: cliente.telefone,
      senha: 'senha123',
    });
    expect(identificacao.clienteIdentificado()?.id).toBe(cliente.id);
    expect(onSucesso).toHaveBeenCalledTimes(1);
  });
});
