import { Component, input, output, ChangeDetectionStrategy, computed } from '@angular/core';
import { CommonModule } from '@angular/common';

export type AbaNavegacaoAutoatendimento = 'inicio' | 'cardapio' | 'carrinho';

/**
 * Footer de navegação entre abas do autoatendimento.
 * Exibe botões para Início, Cardápio e Carrinho (sem perfil).
 *
 * @example
 * <app-autoatendimento-footer-nav
 *   [abaAtual]="abaAtual()"
 *   [totalCarrinho]="carrinho.totalItens()"
 *   (abaMudou)="navegarPara($event)"
 *   (abrirCarrinho)="navegarPara('carrinho')"
 * />
 */
@Component({
    selector: 'app-autoatendimento-footer-nav',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './autoatendimento-footer-nav.component.html',
    styleUrl: './autoatendimento-footer-nav.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class AutoatendimentoFooterNavComponent {
    /** Aba atualmente selecionada */
    readonly abaAtual = input.required<AbaNavegacaoAutoatendimento>();

    /** Total de itens no carrinho */
    readonly totalCarrinho = input<number>(0);

    /** Emitido quando aba muda */
    readonly abaMudou = output<AbaNavegacaoAutoatendimento>();

    /** Emitido quando clica para abrir carrinho */
    readonly abrirCarrinho = output<void>();

    readonly temItensCarrinho = computed(() => (this.totalCarrinho() ?? 0) > 0);
}

