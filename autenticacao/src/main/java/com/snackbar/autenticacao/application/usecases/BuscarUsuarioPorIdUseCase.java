package com.snackbar.autenticacao.application.usecases;

import com.snackbar.autenticacao.application.dtos.UsuarioDTO;
import com.snackbar.autenticacao.domain.entities.Usuario;
import com.snackbar.autenticacao.domain.ports.UsuarioRepositoryPort;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuscarUsuarioPorIdUseCase {
    
    private final UsuarioRepositoryPort usuarioRepository;
    
    public UsuarioDTO executar(String id) {
        Usuario usuario = usuarioRepository.buscarPorId(id)
            .orElseThrow(() -> new ValidationException("Usuário não encontrado"));
        
        return UsuarioDTO.de(usuario);
    }
}

