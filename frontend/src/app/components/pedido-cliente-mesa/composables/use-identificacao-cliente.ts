import { signal, computed, inject } from '@angular/core';
import { PedidoMesaService } from '../../../services/pedido-mesa.service';

export interface ClienteIdentificado {
    id: string;
    nome: string;
    telefone: string;
    novoCliente: boolean;
}

type EtapaIdentificacao = 'identificacao' | 'cadastro';

/**
 * Composable para gerenciar a identificação e cadastro de clientes.
 * Responsabilidade única: fluxo de identificação do cliente.
 */
export function useIdentificacaoCliente(mesaToken: () => string | undefined) {
    const pedidoMesaService = inject(PedidoMesaService);

    // Estado interno
    const _telefoneInput = signal('');
    const _nomeInput = signal('');
    const etapa = signal<EtapaIdentificacao>('identificacao');
    const buscando = signal(false);
    const clienteIdentificado = signal<ClienteIdentificado | null>(null);
    const erro = signal<string | null>(null);

    // Computed para validações
    const telefoneValido = computed(() => {
        const numeros = _telefoneInput().replace(/\D/g, '');
        return numeros.length >= 10 && numeros.length <= 11;
    });

    const nomeValido = computed(() => _nomeInput().trim().length >= 2);

    const podeBuscar = computed(() => telefoneValido() && !buscando());
    const podeCadastrar = computed(() => telefoneValido() && nomeValido() && !buscando());

    // Formatação
    function formatarTelefoneInput(numeros: string): string {
        if (numeros.length === 0) return '';
        if (numeros.length <= 2) return `(${numeros}`;
        if (numeros.length <= 6) return `(${numeros.slice(0, 2)}) ${numeros.slice(2)}`;
        if (numeros.length <= 10) return `(${numeros.slice(0, 2)}) ${numeros.slice(2, 6)}-${numeros.slice(6)}`;
        return `(${numeros.slice(0, 2)}) ${numeros.slice(2, 7)}-${numeros.slice(7, 11)}`;
    }

    function formatarTelefone(telefone: string): string {
        const numeros = telefone.replace(/\D/g, '');
        if (numeros.length === 11) {
            return `(${numeros.slice(0, 2)}) ${numeros.slice(2, 7)}-${numeros.slice(7)}`;
        }
        if (numeros.length === 10) {
            return `(${numeros.slice(0, 2)}) ${numeros.slice(2, 6)}-${numeros.slice(6)}`;
        }
        return telefone;
    }

    // Getters/Setters para binding
    function getTelefone(): string {
        return _telefoneInput();
    }

    function setTelefone(value: string): void {
        const numeros = value.replace(/\D/g, '').slice(0, 11);
        _telefoneInput.set(formatarTelefoneInput(numeros));
    }

    function getNome(): string {
        return _nomeInput();
    }

    function setNome(value: string): void {
        _nomeInput.set(value);
    }

    // Ações
    function buscarCliente(onSucesso: () => void): void {
        if (!podeBuscar()) return;

        const token = mesaToken();
        if (!token) return;

        const telefone = _telefoneInput().replace(/\D/g, '');
        buscando.set(true);
        erro.set(null);

        pedidoMesaService.buscarClientePorTelefone(token, telefone).subscribe({
            next: (cliente) => {
                buscando.set(false);
                clienteIdentificado.set({
                    id: cliente.id,
                    nome: cliente.nome,
                    telefone: cliente.telefone,
                    novoCliente: false
                });
                onSucesso();
            },
            error: (err) => {
                buscando.set(false);
                if (err.status === 404) {
                    etapa.set('cadastro');
                } else {
                    erro.set('Erro ao buscar cliente. Tente novamente.');
                }
            }
        });
    }

    function cadastrarCliente(onSucesso: () => void): void {
        if (!podeCadastrar()) return;

        const token = mesaToken();
        if (!token) return;

        const telefone = _telefoneInput().replace(/\D/g, '');
        const nome = _nomeInput().trim();
        buscando.set(true);
        erro.set(null);

        pedidoMesaService.cadastrarCliente(token, { nome, telefone }).subscribe({
            next: (cliente) => {
                buscando.set(false);
                clienteIdentificado.set({
                    id: cliente.id,
                    nome: cliente.nome,
                    telefone: cliente.telefone,
                    novoCliente: true
                });
                onSucesso();
            },
            error: () => {
                buscando.set(false);
                erro.set('Erro ao cadastrar. Tente novamente.');
            }
        });
    }

    function voltarParaIdentificacao(): void {
        etapa.set('identificacao');
        _nomeInput.set('');
        erro.set(null);
    }

    function trocarCliente(): void {
        clienteIdentificado.set(null);
        _telefoneInput.set('');
        _nomeInput.set('');
        etapa.set('identificacao');
    }

    /**
     * Define o cliente identificado a partir dos dados do login Google.
     * Usado quando o cliente faz login via Google OAuth.
     */
    function setClienteFromGoogle(cliente: { id: string; nome: string; telefone: string }): void {
        clienteIdentificado.set({
            id: cliente.id,
            nome: cliente.nome,
            telefone: cliente.telefone || '',
            novoCliente: false
        });
    }

    return {
        // Estado
        etapa: etapa.asReadonly(),
        buscando: buscando.asReadonly(),
        clienteIdentificado: clienteIdentificado.asReadonly(),
        erro: erro.asReadonly(),

        // Computed
        telefoneValido,
        nomeValido,
        podeBuscar,
        podeCadastrar,

        // Getters/Setters
        getTelefone,
        setTelefone,
        getNome,
        setNome,

        // Ações
        buscarCliente,
        cadastrarCliente,
        voltarParaIdentificacao,
        trocarCliente,
        setClienteFromGoogle,

        // Utilitários
        formatarTelefone
    };
}
