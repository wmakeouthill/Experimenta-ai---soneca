import { TestBed } from '@angular/core/testing';
import { LobbyHeaderComponent } from './header.component';

describe('LobbyHeaderComponent', () => {
  const abrirMenu = (podeConfigurarAnimacao: boolean) => {
    const fixture = TestBed.createComponent(LobbyHeaderComponent);
    fixture.componentRef.setInput('podeConfigurarAnimacao', podeConfigurarAnimacao);
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.lobby-topbar__gear')!.click();
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('[role="menu"]')!.textContent!;
  };

  it('operador troca o piso sem login de admin, mas não configura anúncios', () => {
    const menu = abrirMenu(false);
    expect(menu).toContain('Trocar piso');
    expect(menu).not.toContain('Configurar anúncios');
  });

  it('admin vê também a configuração de anúncios', () => {
    expect(abrirMenu(true)).toContain('Configurar anúncios');
  });
});
