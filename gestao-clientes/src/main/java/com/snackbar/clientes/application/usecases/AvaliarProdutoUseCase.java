package com.snackbar.clientes.application.usecases;

import com.snackbar.clientes.application.dto.AvaliarProdutoRequest;
import com.snackbar.clientes.application.dto.ClienteAvaliacaoDTO;
import com.snackbar.clientes.application.ports.ClienteAvaliacaoRepositoryPort;
import com.snackbar.clientes.application.ports.ClienteRepositoryPort;
import com.snackbar.clientes.domain.entities.ClienteAvaliacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AvaliarProdutoUseCase {

    private final ClienteAvaliacaoRepositoryPort avaliacaoRepository;
    private final ClienteRepositoryPort clienteRepository;

    public ClienteAvaliacaoDTO executar(String clienteId, AvaliarProdutoRequest request) {
        // Validar se cliente existe
        clienteRepository.buscarPorId(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado: " + clienteId));

        // Verificar se já avaliou este produto
        Optional<ClienteAvaliacao> avaliacaoExistente = avaliacaoRepository.buscar(clienteId, request.getProdutoId());

        ClienteAvaliacao avaliacao;

        if (avaliacaoExistente.isPresent()) {
            // Atualizar avaliação existente
            avaliacao = avaliacaoExistente.get();
            avaliacao.atualizar(request.getNota(), request.getComentario());
        } else {
            // Criar nova avaliação
            avaliacao = ClienteAvaliacao.criar(
                    clienteId,
                    request.getProdutoId(),
                    request.getNota(),
                    request.getComentario());
        }

        ClienteAvaliacao salva = avaliacaoRepository.salvar(avaliacao);
        return ClienteAvaliacaoDTO.de(salva);
    }
}
