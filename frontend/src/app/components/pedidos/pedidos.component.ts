import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  NgZone,
  OnDestroy,
  OnInit,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { catchError, of, Subscription, switchMap, takeWhile, timer } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { FilaPedidosMesaService, PedidoPendente } from '../../services/fila-pedidos-mesa.service';
import { FilaPedidosTotemService } from '../../services/fila-pedidos-totem.service';
import { ImpressaoService } from '../../services/impressao.service';
import { NotificationService } from '../../services/notification.service';
import { Pedido, PedidoService, StatusPedido } from '../../services/pedido.service';
import { SessaoTrabalho, SessaoTrabalhoService } from '../../services/sessao-trabalho.service';
import { gerarUuid } from '../../shared/utils/uuid';
import { MenuContextoPedidoComponent } from './components/menu-contexto-pedido/menu-contexto-pedido.component';
import { NovoPedidoModalComponent } from './components/novo-pedido-modal/novo-pedido-modal.component';
import { usePedidos } from './composables/use-pedidos';

@Component({
  selector: 'app-pedidos',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    FormsModule,
    NovoPedidoModalComponent,
    MenuContextoPedidoComponent,
  ],
  templateUrl: './pedidos.component.html',
  styleUrl: './pedidos.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PedidosComponent implements OnInit, OnDestroy {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);
  private readonly pedidoService = inject(PedidoService);
  private readonly sessaoService = inject(SessaoTrabalhoService);
  private readonly authService = inject(AuthService);
  private readonly impressaoService = inject(ImpressaoService);
  private readonly notificationService = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly filaPedidosMesaService = inject(FilaPedidosMesaService);
  private readonly filaPedidosTotemService = inject(FilaPedidosTotemService);
  private readonly ngZone = inject(NgZone);

  // Subscription para polling de fila de mesa e totem
  private filaPollingSubscription?: Subscription;
  private filaTotemPollingSubscription?: Subscription;
  private filaPollingAtivo = false;
  private filaTotemPollingAtivo = false;

  // Composable com toda a lógica de pedidos - inicializado no construtor para contexto de injeção válido
  readonly pedidosComposable!: ReturnType<typeof usePedidos>;

  // Expor propriedades necessárias (usando getters para acessar após inicialização)
  get pedidosFiltrados() {
    return this.pedidosComposable.pedidosFiltrados;
  }

  get pedidosPorStatus() {
    return this.pedidosComposable.pedidosPorStatus;
  }

  get estado() {
    return this.pedidosComposable.estado;
  }

  get erro() {
    return this.pedidosComposable.erro;
  }

  get estaCarregando() {
    return this.pedidosComposable.estaCarregando;
  }

  get temPedidos() {
    return this.pedidosComposable.temPedidos;
  }

  get pesquisaTexto() {
    return this.pedidosComposable.pesquisaTexto;
  }

  get produtos() {
    return this.pedidosComposable.produtos;
  }

  readonly StatusPedido = StatusPedido;

  // Modal/Formulário states
  readonly mostrarFormulario = signal(false);

  // Menu de contexto
  readonly menuContexto = signal<{ pedidoId: string; x: number; y: number } | null>(null);

  // Sessão ativa
  readonly temSessaoAtiva = signal<boolean>(false);
  readonly sessaoAtiva = signal<SessaoTrabalho | null>(null);

  // Fila de pedidos de mesa pendentes
  readonly pedidosPendentesMesa = signal<PedidoPendente[]>([]);
  readonly carregandoFilaMesa = signal(false);
  readonly filtroFilaMesa = signal(false);

  // Fila de pedidos do totem pendentes
  readonly pedidosPendentesTotem = signal<PedidoPendente[]>([]);
  readonly carregandoFilaTotem = signal(false);
  readonly filtroFilaTotem = signal(false);

  constructor() {
    this.pedidosComposable = usePedidos();
  }

  ngOnInit(): void {
    if (this.isBrowser) {
      // verificarSessaoAtiva já chama carregarDados internamente
      this.verificarSessaoAtiva();
      this.pedidosComposable.carregarProdutos();
      this.iniciarPollingFilaMesa();
      this.iniciarPollingFilaTotem();

      // Notificação visual apenas (impressão é global no AppComponent)
      this.pedidosComposable.onNovoPedido
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(pedido => {
          // A notificação global já é disparada pelo AppComponent
          // Não precisamos duplicar aqui
        });

      document.addEventListener('click', () => this.fecharMenuContexto());
      document.addEventListener('contextmenu', e => {
        const target = e.target as HTMLElement;
        if (!target.closest('.pedido-card')) {
          this.fecharMenuContexto();
        }
      });
    }
  }

  ngOnDestroy(): void {
    this.filaPollingAtivo = false;
    this.filaTotemPollingAtivo = false;
    this.filaPollingSubscription?.unsubscribe();
    this.filaTotemPollingSubscription?.unsubscribe();
  }

  private iniciarPollingFilaMesa(): void {
    if (this.filaPollingAtivo) {
      console.log('Polling de fila de mesa já está ativo.');
      return;
    }

    this.filaPollingAtivo = true;
    this.filaPollingSubscription?.unsubscribe();

    // Executa fora da zona Angular para não bloquear hidratação/estabilidade
    this.ngZone.runOutsideAngular(() => {
      this.filaPollingSubscription = timer(0, 5000)
        .pipe(
          takeWhile(() => this.filaPollingAtivo),
          switchMap(() => this.filaPedidosMesaService.listarPedidosPendentes()),
          catchError(err => {
            console.error('Erro ao carregar fila de mesa:', err);
            return of([]);
          })
        )
        .subscribe(pedidos => {
          // Executa atualizações de estado dentro da zona Angular para trigger change detection
          this.ngZone.run(() => {
            const pedidosAnteriores = this.pedidosPendentesMesa();
            this.pedidosPendentesMesa.set([...pedidos]); // Nova referência de array

            // Notificar se há novos pedidos de mesa
            if (pedidos.length > pedidosAnteriores.length) {
              const novos = pedidos.length - pedidosAnteriores.length;
              this.notificationService.info(
                `🍽️ ${novos} novo(s) pedido(s) de mesa aguardando aceitação!`
              );
            }
          });
        });
    });
  }

  private iniciarPollingFilaTotem(): void {
    if (this.filaTotemPollingAtivo) return;
    this.filaTotemPollingAtivo = true;
    this.filaTotemPollingSubscription?.unsubscribe();
    this.ngZone.runOutsideAngular(() => {
      this.filaTotemPollingSubscription = timer(0, 5000)
        .pipe(
          takeWhile(() => this.filaTotemPollingAtivo),
          switchMap(() => this.filaPedidosTotemService.listarPedidosPendentes()),
          catchError(err => {
            console.error('Erro ao carregar fila totem:', err);
            return of([]);
          })
        )
        .subscribe(pedidos => {
          this.ngZone.run(() => {
            const anteriores = this.pedidosPendentesTotem();
            this.pedidosPendentesTotem.set([...pedidos]);
            if (pedidos.length > anteriores.length) {
              const novos = pedidos.length - anteriores.length;
              this.notificationService.info(
                `🖥️ ${novos} novo(s) pedido(s) do totem aguardando aceitação!`
              );
            }
          });
        });
    });
  }

  verificarSessaoAtiva(): void {
    this.sessaoService.buscarAtiva().subscribe({
      next: sessao => {
        if (sessao) {
          this.temSessaoAtiva.set(true);
          this.sessaoAtiva.set(sessao);
          // Recarrega pedidos quando sessão mudar para filtrar pela nova sessão
          this.carregarDados();
        } else {
          // null significa que não há sessão ativa (404 esperado e tratado silenciosamente)
          this.temSessaoAtiva.set(false);
          this.sessaoAtiva.set(null);
          // Recarrega pedidos sem filtro de sessão quando não há sessão ativa
          this.carregarDados();
        }
      },
      error: error => {
        // Apenas erros reais (não 404) chegam aqui
        // 404 é tratado no serviço e retorna null no next, não chega aqui
        // Não logar erro aqui para evitar logs desnecessários
        this.temSessaoAtiva.set(false);
        this.sessaoAtiva.set(null);
        // Recarrega pedidos sem filtro de sessão em caso de erro
        this.carregarDados();
      },
    });
  }

  private carregarDados(): void {
    const sessaoId = this.sessaoAtiva()?.id;
    // Inicia o polling ao invés de apenas carregar uma vez
    this.pedidosComposable.iniciarPolling(sessaoId);
    this.pedidosComposable.carregarProdutos();
  }

  filtrarPorStatus(status: StatusPedido): void {
    this.filtroFilaMesa.set(false);
    this.filtroFilaTotem.set(false);
    this.pedidosComposable.filtrarPorStatus(status);
  }

  pesquisar(texto: string): void {
    this.pedidosComposable.pesquisar(texto);
  }

  limparFiltros(): void {
    this.pedidosComposable.limparFiltros();
    this.filtroFilaMesa.set(false);
    this.filtroFilaTotem.set(false);
  }

  filtrarPorFilaMesa(): void {
    this.pedidosComposable.limparFiltros();
    this.filtroFilaTotem.set(false);
    this.filtroFilaMesa.set(true);
  }

  filtrarPorFilaTotem(): void {
    this.pedidosComposable.limparFiltros();
    this.filtroFilaMesa.set(false);
    this.filtroFilaTotem.set(true);
  }

  recarregar(): void {
    this.carregarDados();
  }

  // Métodos de fila de pedidos de mesa
  aceitarPedidoMesa(pedidoId: string): void {
    if (!this.temSessaoAtiva()) {
      if (this.isBrowser) {
        alert('É necessário ter uma sessão de trabalho ativa para aceitar pedidos.');
      }
      return;
    }

    this.carregandoFilaMesa.set(true);
    const idempotencyKey = gerarUuid();
    this.filaPedidosMesaService.aceitarPedido(pedidoId, idempotencyKey).subscribe({
      next: pedidoCriado => {
        this.notificationService.sucesso('✅ Pedido aceito e criado com sucesso!');
        // Remove da fila local
        this.pedidosPendentesMesa.update(lista => lista.filter(p => p.id !== pedidoId));
        // Recarrega pedidos para mostrar o novo
        this.carregarDados();
        this.carregandoFilaMesa.set(false);
      },
      error: error => {
        console.error('Erro ao aceitar pedido:', error);
        this.notificationService.erro(
          '❌ Erro ao aceitar pedido: ' + (error.error?.message || error.message)
        );
        this.carregandoFilaMesa.set(false);
      },
    });
  }

  rejeitarPedidoMesa(pedidoId: string): void {
    if (!this.isBrowser) return;

    const motivo = prompt('Motivo da rejeição (opcional):');
    if (motivo === null) return;

    this.carregandoFilaMesa.set(true);
    this.filaPedidosMesaService.rejeitarPedido(pedidoId, motivo || undefined).subscribe({
      next: () => {
        this.notificationService.info('Pedido rejeitado.');
        this.pedidosPendentesMesa.update(lista => lista.filter(p => p.id !== pedidoId));
        this.carregandoFilaMesa.set(false);
      },
      error: error => {
        console.error('Erro ao rejeitar pedido:', error);
        this.notificationService.erro(
          '❌ Erro ao rejeitar pedido: ' + (error.error?.message || error.message)
        );
        this.carregandoFilaMesa.set(false);
      },
    });
  }

  // Fila de pedidos do totem
  aceitarPedidoTotem(pedidoId: string): void {
    if (!this.temSessaoAtiva()) {
      if (this.isBrowser) {
        alert('É necessário ter uma sessão de trabalho ativa para aceitar pedidos.');
      }
      return;
    }
    this.carregandoFilaTotem.set(true);
    const idempotencyKey = this.filaPedidosTotemService.gerarChaveIdempotencia();
    this.filaPedidosTotemService.aceitarPedido(pedidoId, idempotencyKey).subscribe({
      next: () => {
        this.notificationService.sucesso('✅ Pedido totem aceito e criado com sucesso!');
        this.pedidosPendentesTotem.update(lista => lista.filter(p => p.id !== pedidoId));
        this.carregarDados();
        this.carregandoFilaTotem.set(false);
      },
      error: error => {
        console.error('Erro ao aceitar pedido totem:', error);
        this.notificationService.erro(
          '❌ Erro ao aceitar pedido: ' + (error.error?.message || error.message)
        );
        this.carregandoFilaTotem.set(false);
      },
    });
  }

  rejeitarPedidoTotem(pedidoId: string): void {
    if (!this.isBrowser) return;
    const motivo = prompt('Motivo da rejeição (opcional):');
    if (motivo === null) return;
    this.carregandoFilaTotem.set(true);
    this.filaPedidosTotemService.rejeitarPedido(pedidoId, motivo || undefined).subscribe({
      next: () => {
        this.notificationService.info('Pedido rejeitado.');
        this.pedidosPendentesTotem.update(lista => lista.filter(p => p.id !== pedidoId));
        this.carregandoFilaTotem.set(false);
      },
      error: error => {
        console.error('Erro ao rejeitar pedido totem:', error);
        this.notificationService.erro(
          '❌ Erro ao rejeitar pedido: ' + (error.error?.message || error.message)
        );
        this.carregandoFilaTotem.set(false);
      },
    });
  }

  formatarTempoEspera(segundos: number): string {
    return this.filaPedidosMesaService.formatarTempoEspera(segundos);
  }

  formatarTempoEsperaTotem(segundos: number): string {
    return this.filaPedidosTotemService.formatarTempoEspera(segundos);
  }

  getClasseTempoEsperaTotem(segundos: number): string {
    return this.filaPedidosTotemService.getClasseTempoEspera(segundos);
  }

  getClasseTempoEspera(segundos: number): string {
    return this.filaPedidosMesaService.getClasseTempoEspera(segundos);
  }

  abrirFormulario(): void {
    if (!this.temSessaoAtiva()) {
      if (this.isBrowser) {
        alert('É necessário iniciar uma sessão de trabalho para criar pedidos.');
      }
      return;
    }
    this.mostrarFormulario.set(true);
  }

  fecharFormulario(): void {
    this.mostrarFormulario.set(false);
  }

  onPedidoCriado(request: {
    clienteId: string;
    clienteNome: string;
    itens: any[];
    meiosPagamento: any[];
    observacoes?: string;
  }): void {
    // Validar se usuário está logado
    const usuario = this.authService.usuarioAtual();
    if (!usuario?.id) {
      const mensagem =
        'É necessário estar logado para criar um pedido. Por favor, faça login novamente.';
      if (this.isBrowser) {
        alert(mensagem);
      }
      console.error('Erro: Usuário não está logado');
      return;
    }

    // Adicionar usuarioId do usuário logado (obrigatório)
    const requestComUsuario = {
      ...request,
      usuarioId: usuario.id,
    };

    // Gera chave de idempotência UMA VEZ para esta operação de criação
    const idempotencyKey = this.pedidoService.gerarChaveIdempotencia();

    this.pedidoService.criar(requestComUsuario, idempotencyKey).subscribe({
      next: pedidoCriado => {
        this.pedidosComposable.atualizarPedidoNoSignal(pedidoCriado);
        setTimeout(() => {
          const sessaoId = this.sessaoAtiva()?.id;
          this.pedidosComposable.carregarPedidos(sessaoId ? { sessaoId } : undefined);
        }, 0);
        this.fecharFormulario();
      },
      error: error => {
        console.error('Erro ao criar pedido:', error);
        let mensagem =
          'Erro ao criar pedido. Verifique se todos os campos estão preenchidos corretamente.';

        if (error.error?.message) {
          mensagem = error.error.message;
        } else if (error.error?.errors) {
          const erros = Object.values(error.error.errors).join(', ');
          mensagem = `Erro de validação: ${erros}`;
        } else if (error.message) {
          mensagem = error.message;
        }

        if (this.isBrowser) {
          alert(mensagem);
        }
      },
    });
  }

  atualizarStatus(pedidoId: string, novoStatus: StatusPedido): void {
    this.pedidoService.atualizarStatus(pedidoId, novoStatus).subscribe({
      next: pedidoAtualizado => {
        // Atualiza o pedido no signal imediatamente para atualizar os contadores em tempo real
        this.pedidosComposable.atualizarPedidoNoSignal(pedidoAtualizado);
        // Recarrega todos os pedidos de forma assíncrona para garantir sincronização completa
        // Usa setTimeout para garantir que a atualização imediata seja processada primeiro
        setTimeout(() => {
          const sessaoId = this.sessaoAtiva()?.id;
          this.pedidosComposable.carregarPedidos(sessaoId ? { sessaoId } : undefined);
        }, 0);
      },
      error: error => {
        console.error('Erro ao atualizar status:', error);
        let mensagem = 'Erro ao atualizar status. Tente novamente.';

        if (error.error) {
          if (error.error.message) {
            mensagem = error.error.message;
          } else if (error.error.errors) {
            const erros = Object.values(error.error.errors).join(', ');
            mensagem = `Erro de validação: ${erros}`;
          }
        } else if (error.message) {
          mensagem = error.message;
        }

        if (this.isBrowser) {
          alert(mensagem);
        }
      },
    });
  }

  cancelarPedido(pedidoId: string): void {
    if (!this.isBrowser || !confirm('Tem certeza que deseja cancelar este pedido?')) {
      return;
    }

    this.pedidoService.cancelar(pedidoId).subscribe({
      next: pedidoCancelado => {
        // Atualiza o signal imediatamente para atualizar contadores em tempo real
        this.pedidosComposable.atualizarPedidoNoSignal(pedidoCancelado);
        // Recarrega todos os pedidos de forma assíncrona para garantir sincronização
        setTimeout(() => {
          const sessaoId = this.sessaoAtiva()?.id;
          this.pedidosComposable.carregarPedidos(sessaoId ? { sessaoId } : undefined);
        }, 0);
      },
      error: error => {
        console.error('Erro ao cancelar pedido:', error);
        if (this.isBrowser) {
          alert('Erro ao cancelar pedido. Tente novamente.');
        }
      },
    });
  }

  // Menu de contexto
  abrirMenuContexto(event: MouseEvent, pedidoId: string): void {
    event.preventDefault();
    event.stopPropagation();

    this.menuContexto.set({
      pedidoId,
      x: event.clientX,
      y: event.clientY,
    });
  }

  fecharMenuContexto(): void {
    this.menuContexto.set(null);
  }

  onStatusAlteradoViaMenu(event: { pedidoId: string; novoStatus: StatusPedido }): void {
    this.fecharMenuContexto();
    this.atualizarStatus(event.pedidoId, event.novoStatus);
  }

  onCancelarViaMenu(pedidoId: string): void {
    this.fecharMenuContexto();
    this.cancelarPedido(pedidoId);
  }

  onImprimirSegundaViaViaMenu(pedidoId: string): void {
    this.fecharMenuContexto();
    this.imprimirSegundaVia(pedidoId);
  }

  onCorrigirTrocoViaMenu(pedidoId: string): void {
    this.fecharMenuContexto();
    const pedido = this.pedidosFiltrados().find(p => p.id === pedidoId);
    if (pedido) {
      this.corrigirTroco(pedidoId, pedido.valorTotal);
    }
  }

  /**
   * Abre um prompt para o operador informar/corrigir o valor pago em dinheiro.
   * Calcula e exibe o troco antes de confirmar.
   */
  corrigirTroco(pedidoId: string, valorTotal: number): void {
    if (!this.isBrowser) return;

    const valorStr = prompt(
      `💵 Informe o valor pago em dinheiro pelo cliente:\n(Valor do pedido: R$ ${valorTotal.toFixed(2).replace('.', ',')})`
    );
    if (valorStr === null) return; // Usuário cancelou

    const valorPago = parseFloat(valorStr.replace(',', '.'));
    if (isNaN(valorPago) || valorPago <= 0) {
      alert('Valor inválido. Informe um valor numérico maior que zero.');
      return;
    }

    if (valorPago < valorTotal) {
      alert(
        `O valor pago (R$ ${valorPago.toFixed(2).replace('.', ',')}) não pode ser menor que o valor do pedido (R$ ${valorTotal.toFixed(2).replace('.', ',')}).`
      );
      return;
    }

    const troco = valorPago - valorTotal;
    const confirmar = confirm(
      `Confirmar correção de troco?\n\nValor do pedido: R$ ${valorTotal.toFixed(2).replace('.', ',')}\nValor pago: R$ ${valorPago.toFixed(2).replace('.', ',')}\nTroco: R$ ${troco.toFixed(2).replace('.', ',')}`
    );
    if (!confirmar) return;

    this.pedidoService.corrigirTroco(pedidoId, valorPago).subscribe({
      next: pedidoAtualizado => {
        this.pedidosComposable.atualizarPedidoNoSignal(pedidoAtualizado);
        this.notificationService.sucesso('✅ Troco corrigido com sucesso!');
        setTimeout(() => {
          const sessaoId = this.sessaoAtiva()?.id;
          this.pedidosComposable.carregarPedidos(sessaoId ? { sessaoId } : undefined);
        }, 0);
      },
      error: error => {
        console.error('Erro ao corrigir troco:', error);
        const mensagem =
          error.error?.message || error.message || 'Erro ao corrigir troco. Tente novamente.';
        if (this.isBrowser) {
          alert(mensagem);
        }
      },
    });
  }

  formatarMeioPagamento(meio: string): string {
    const nomes: Record<string, string> = {
      PIX: 'Pix',
      CARTAO_CREDITO: 'Cartão Crédito',
      CARTAO_DEBITO: 'Cartão Débito',
      VALE_REFEICAO: 'Vale Refeição',
      DINHEIRO: 'Dinheiro',
    };
    return nomes[meio] || meio;
  }

  obterPedidoDoMenu(): Pedido | null {
    const menu = this.menuContexto();
    if (!menu) return null;
    return this.pedidosFiltrados().find(p => p.id === menu.pedidoId) || null;
  }

  formatarData(data: string): string {
    return new Date(data).toLocaleString('pt-BR');
  }

  formatarPreco(preco: number): string {
    return `R$ ${preco.toFixed(2).replace('.', ',')}`;
  }

  formatarStatus(status: StatusPedido): string {
    const nomes: Record<StatusPedido, string> = {
      [StatusPedido.PENDENTE]: 'Aguardando',
      [StatusPedido.PREPARANDO]: 'Preparando',
      [StatusPedido.PRONTO]: 'Pronto',
      [StatusPedido.FINALIZADO]: 'Finalizado',
      [StatusPedido.CANCELADO]: 'Cancelado',
    };
    return nomes[status] || status;
  }

  truncarNomeCliente(nomeCompleto: string): string {
    const palavras = nomeCompleto.trim().split(/\s+/);
    if (palavras.length <= 3) {
      return nomeCompleto;
    }
    return palavras.slice(0, 3).join(' ') + '...';
  }

  imprimirSegundaVia(pedidoId: string): void {
    this.impressaoService
      .buscarConfiguracao()
      .pipe(
        catchError(() => {
          if (this.isBrowser) {
            alert(
              'Erro ao buscar configuração de impressora. Verifique se a impressora está configurada.'
            );
          }
          return of(null);
        })
      )
      .subscribe(config => {
        if (!config || !config.ativa) {
          if (this.isBrowser) {
            alert(
              'Impressora não configurada ou inativa. Configure a impressora em Administração.'
            );
          }
          return;
        }

        this.impressaoService
          .imprimirCupom({
            pedidoId,
            tipoImpressora: config.tipoImpressora,
            nomeEstabelecimento: config.nomeEstabelecimento,
            enderecoEstabelecimento: config.enderecoEstabelecimento,
            telefoneEstabelecimento: config.telefoneEstabelecimento,
            cnpjEstabelecimento: config.cnpjEstabelecimento,
          })
          .pipe(
            catchError(error => {
              console.error('Erro ao imprimir segunda via:', error);
              if (this.isBrowser) {
                alert(
                  'Erro ao imprimir cupom: ' +
                    (error.error?.message || error.message || 'Erro desconhecido')
                );
              }
              this.notificationService.erro(
                'Erro ao imprimir cupom: ' +
                  (error.error?.message || error.message || 'Erro desconhecido')
              );
              return of(null);
            })
          )
          .subscribe(response => {
            if (response?.sucesso) {
              this.notificationService.sucesso('✅ Cupom impresso com sucesso!');
            } else if (response && !response.sucesso) {
              this.notificationService.erro('❌ Erro ao imprimir: ' + response.mensagem);
            }
          });
      });
  }
}
