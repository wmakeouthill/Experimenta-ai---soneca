package com.snackbar.pedidos.infrastructure.idempotency;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snackbar.kernel.domain.exceptions.BusinessRuleException;
import com.snackbar.kernel.domain.exceptions.ConflitoException;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository repository;

    private IdempotencyService service() {
        return new IdempotencyService(repository, new ObjectMapper());
    }

    @Test
    void mesmaChaveEmParaleloNaoExecutaDuasVezes() {
        IdempotencyKeyEntity emAndamento = IdempotencyKeyEntity.builder().idempotencyKey("k").endpoint("/e").build();
        when(repository.findByKeyAndEndpoint(eq("k"), eq("/e"), any()))
                .thenReturn(Optional.empty(), Optional.of(emAndamento));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));
        Supplier<String> operacao = () -> {
            throw new AssertionError("não pode executar");
        };

        assertThrows(ConflitoException.class, () -> service().executeIdempotent("k", "/e", operacao, String.class));
    }

    @Test
    void operacaoQueFalhaLiberaAChave() {
        when(repository.findByKeyAndEndpoint(eq("k"), eq("/e"), any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenReturn(IdempotencyKeyEntity.builder().id(7L).build());
        Supplier<String> operacao = () -> {
            throw new BusinessRuleException("loja fechada");
        };

        assertThrows(BusinessRuleException.class, () -> service().executeIdempotent("k", "/e", operacao, String.class));
        verify(repository).deleteById(7L);
    }
}
