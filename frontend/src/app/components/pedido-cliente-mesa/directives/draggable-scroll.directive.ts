import { Directive, ElementRef, OnInit, OnDestroy, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

/**
 * Diretiva que adiciona scroll horizontal por drag do mouse (desktop).
 * No touch o scroll nativo cuida de tudo (inércia + snap do CSS); interceptar
 * touchmove quebrava a inércia e travava o scroll vertical da página.
 *
 * Uso: <div class="carrossel-horizontal" appDraggableScroll>
 */
@Directive({
    selector: '[appDraggableScroll]',
    standalone: true
})
export class DraggableScrollDirective implements OnInit, OnDestroy {
    private readonly el = inject(ElementRef);
    private readonly platformId = inject(PLATFORM_ID);

    private isDown = false;
    private startX = 0;
    private scrollLeft = 0;
    private moved = false;

    // Bound handlers for proper removal
    private boundMouseDown = this.onMouseDown.bind(this);
    private boundMouseUp = this.onMouseUp.bind(this);
    private boundMouseMove = this.onMouseMove.bind(this);
    private boundClick = this.onClick.bind(this);

    ngOnInit(): void {
        if (!isPlatformBrowser(this.platformId)) return;

        const element = this.el.nativeElement as HTMLElement;

        element.addEventListener('mousedown', this.boundMouseDown);
        element.addEventListener('mouseleave', this.boundMouseUp);
        element.addEventListener('mouseup', this.boundMouseUp);
        element.addEventListener('mousemove', this.boundMouseMove);
        element.addEventListener('click', this.boundClick, true);

        element.style.cursor = 'grab';
    }

    ngOnDestroy(): void {
        if (!isPlatformBrowser(this.platformId)) return;

        const element = this.el.nativeElement as HTMLElement;

        element.removeEventListener('mousedown', this.boundMouseDown);
        element.removeEventListener('mouseleave', this.boundMouseUp);
        element.removeEventListener('mouseup', this.boundMouseUp);
        element.removeEventListener('mousemove', this.boundMouseMove);
        element.removeEventListener('click', this.boundClick, true);
    }

    private onMouseDown(e: MouseEvent): void {
        const element = this.el.nativeElement as HTMLElement;
        this.isDown = true;
        this.moved = false;
        element.style.cursor = 'grabbing';
        // Snap desligado durante o arraste, senão cada scrollLeft "pula" pro card
        element.style.scrollSnapType = 'none';
        this.startX = e.pageX - element.offsetLeft;
        this.scrollLeft = element.scrollLeft;
    }

    private onMouseUp(): void {
        if (!this.isDown) return;
        this.isDown = false;
        const element = this.el.nativeElement as HTMLElement;
        element.style.cursor = 'grab';
        element.style.scrollSnapType = '';
    }

    private onMouseMove(e: MouseEvent): void {
        if (!this.isDown) return;
        e.preventDefault();

        const element = this.el.nativeElement as HTMLElement;
        const x = e.pageX - element.offsetLeft;
        const walk = (x - this.startX) * 1.5; // Multiplicador de velocidade

        if (Math.abs(walk) > 5) {
            this.moved = true;
        }

        element.scrollLeft = this.scrollLeft - walk;
    }

    private onClick(e: MouseEvent): void {
        // Previne cliques quando o usuário estava arrastando
        if (this.moved) {
            e.preventDefault();
            e.stopPropagation();
            this.moved = false;
        }
    }
}
