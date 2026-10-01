import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { ConfigImpressoraComponent } from './config-impressora.component';
import { ImpressaoService, TipoImpressora } from '../../../../services/impressao.service';
import { AuthService } from '../../../../services/auth.service';
import { ElectronImpressoraService } from '../../../../services/electron-impressora.service';

describe('ConfigImpressoraComponent no Electron', () => {
  it('mantém Daruma/Epson ao carregar, salvar e testar; seleciona Windows pelo nome', () => {
    const impressao = jasmine.createSpyObj<ImpressaoService>('ImpressaoService',
      ['buscarConfiguracao', 'salvarConfiguracao', 'imprimirCupomTeste']);
    impressao.salvarConfiguracao.and.returnValue(of({ tipoImpressora: TipoImpressora.GENERICA_ESCPOS, nomeEstabelecimento: 'Teste' }));
    impressao.imprimirCupomTeste.and.returnValue(of({ sucesso: true, mensagem: 'ok', dataImpressao: '', pedidoId: 'teste' }));
    TestBed.configureTestingModule({ providers: [
      { provide: ImpressaoService, useValue: impressao },
      { provide: AuthService, useValue: { isAdministrador: signal(true) } },
      { provide: ElectronImpressoraService, useValue: { estaRodandoNoElectron: () => true } },
    ] });
    const componente = TestBed.runInInjectionContext(() => new ConfigImpressoraComponent());
    const impressora = { name: 'EPSON TM-T20X Receipt (Copy 1)', devicePath: 'USB002', status: 'Normal', padrao: false, tipo: 'windows' as const };
    componente.impressorasDisponiveis.set([impressora]);

    for (const tipo of [TipoImpressora.DARUMA_800, TipoImpressora.EPSON_TM_T20, TipoImpressora.GENERICA_ESCPOS]) {
      impressao.buscarConfiguracao.and.returnValue(of({ tipoImpressora: tipo, devicePath: 'USB002', nomeEstabelecimento: 'Teste' }));
      componente.carregarConfiguracao();
      expect(componente.formImpressora.value.tipoImpressora).toBe(tipo);
      componente.selecionarImpressora(impressora);
      expect(componente.formImpressora.value.devicePath).toBe(impressora.name);
      componente.formImpressora.patchValue({ devicePath: 'USB002' });
      componente.formImpressora.patchValue({ devicePath: impressora.name });
      expect(componente.erroDevicePath()).toBeNull();
      componente.salvarConfiguracao();
      expect(impressao.salvarConfiguracao.calls.mostRecent().args[0].tipoImpressora).toBe(tipo);
      componente.testarImpressao();
      expect(impressao.imprimirCupomTeste.calls.mostRecent().args[0].tipoImpressora).toBe(tipo);
    }
  });
});
