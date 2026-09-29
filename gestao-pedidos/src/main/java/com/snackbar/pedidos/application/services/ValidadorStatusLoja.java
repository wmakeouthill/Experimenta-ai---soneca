package com.snackbar.pedidos.application.services;

import org.springframework.stereotype.Service;

import com.snackbar.kernel.domain.exceptions.BusinessRuleException;
import com.snackbar.pedidos.application.ports.SessaoTrabalhoRepositoryPort;
import com.snackbar.pedidos.domain.entities.SessaoTrabalho;
import com.snackbar.pedidos.domain.entities.StatusSessao;

import lombok.RequiredArgsConstructor;

/**
 * Quando a loja recebe pedido: sem sessão ativa (FECHADA) nenhum canal cria pedido;
 * com a sessão PAUSADA o balcão segue e mesa/totem são recusados.
 * Todo caminho que cria pedido passa por aqui, para nenhum pedido nascer sem sessão.
 */
@Service
@RequiredArgsConstructor
public class ValidadorStatusLoja {

    private final SessaoTrabalhoRepositoryPort sessaoTrabalhoRepository;

    /** Balcão (operador): vale sessão ABERTA ou PAUSADA. */
    public SessaoTrabalho exigirSessaoAtiva() {
        return sessaoTrabalhoRepository.buscarSessaoAtiva()
                .orElseThrow(() -> new BusinessRuleException(
                        "Nenhuma sessão de trabalho aberta. Abra a sessão antes de lançar pedidos."));
    }

    /** Mesa e totem (cliente): só com a sessão ABERTA. */
    public SessaoTrabalho exigirLojaAberta() {
        return sessaoTrabalhoRepository.buscarSessaoAtiva()
                .filter(sessao -> sessao.getStatus() == StatusSessao.ABERTA)
                .orElseThrow(() -> new BusinessRuleException(
                        "A loja não está recebendo pedidos no momento. Tente novamente em instantes."));
    }
}
