package com.snackbar.pedidos.infrastructure.idempotency;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snackbar.kernel.domain.exceptions.ConflitoException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de idempotência para garantir que operações duplicadas
 * retornem a mesma resposta sem executar a operação novamente.
 * 
 * Uso típico:
 * 
 * <pre>
 * return idempotencyService.executeIdempotent(
 *         idempotencyKey,
 *         "/api/pedidos",
 *         () -> criarPedidoUseCase.executar(request));
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    /**
     * Tempo de expiração padrão das chaves de idempotência (24 horas).
     */
    private static final long EXPIRATION_HOURS = 24;

    /**
     * Executa uma operação de forma idempotente.
     * 
     * Reserva a chave (linha sem resposta) ANTES de executar: a constraint UNIQUE
     * garante que só uma requisição com a mesma chave executa; as outras recebem
     * a resposta salva ou 409 enquanto a primeira não termina. Se a operação
     * falhar, a reserva é apagada e o cliente pode repetir com a mesma chave.
     * 
     * IMPORTANTE: Este método NÃO é @Transactional propositalmente: a reserva
     * precisa estar comitada antes da operação, que gerencia a própria transação.
     * ponytail: reserva órfã (queda do servidor no meio da operação) segura a chave
     * até expirar (24 h); o front gera chave nova por clique, então só o reenvio
     * da mesma chave recebe 409. Se isso pesar, tratar reserva velha como livre.
     * 
     * @param idempotencyKey Chave única da requisição
     * @param endpoint       Endpoint da API (para evitar colisão entre endpoints)
     * @param operation      Operação a ser executada
     * @param responseType   Tipo da resposta para deserialização
     * @return ResponseEntity com a resposta (nova ou cached)
     */
    public <T> ResponseEntity<T> executeIdempotent(
            String idempotencyKey,
            String endpoint,
            Supplier<T> operation,
            Class<T> responseType) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            // Sem chave de idempotência, executa normalmente
            T result = operation.get();
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        }

        Optional<IdempotencyKeyEntity> existingKey = idempotencyKeyRepository
                .findByKeyAndEndpoint(idempotencyKey, endpoint, LocalDateTime.now());
        if (existingKey.isPresent()) {
            return responderDuplicada(existingKey.get(), responseType);
        }

        IdempotencyKeyEntity reserva;
        try {
            reserva = idempotencyKeyRepository.saveAndFlush(IdempotencyKeyEntity.builder()
                    .idempotencyKey(idempotencyKey)
                    .endpoint(endpoint)
                    .expiresAt(LocalDateTime.now().plusHours(EXPIRATION_HOURS))
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Outra requisição reservou a chave entre a consulta e o INSERT
            return responderDuplicada(idempotencyKeyRepository
                    .findByKeyAndEndpoint(idempotencyKey, endpoint, LocalDateTime.now())
                    .orElseThrow(() -> new ConflitoException("Chave de idempotência já usada. Gere uma nova.")),
                    responseType);
        }

        T result;
        try {
            result = operation.get();
        } catch (RuntimeException e) {
            idempotencyKeyRepository.deleteById(reserva.getId());
            throw e;
        }

        HttpStatus status = HttpStatus.CREATED;
        try {
            reserva.setResponseBody(objectMapper.writeValueAsString(result));
            reserva.setResponseStatus(status.value());
            idempotencyKeyRepository.save(reserva);
        } catch (JsonProcessingException | RuntimeException e) {
            // A operação já executou: devolve o resultado; a reserva fica e o reenvio recebe 409, sem duplicar
            log.error("[IDEMPOTENCY] Erro ao salvar resposta para idempotência - Key: {}", idempotencyKey, e);
        }

        log.debug("[IDEMPOTENCY] Nova chave registrada - Key: {}, Endpoint: {}",
                idempotencyKey, endpoint);

        return ResponseEntity.status(status).body(result);
    }

    /** Devolve a resposta salva da chave, ou 409 se a primeira requisição ainda não terminou. */
    private <T> ResponseEntity<T> responderDuplicada(IdempotencyKeyEntity cached, Class<T> responseType) {
        log.info("[IDEMPOTENCY] Requisição duplicada detectada - Key: {}, Endpoint: {}",
                cached.getIdempotencyKey(), cached.getEndpoint());
        if (cached.getResponseStatus() == null) {
            throw new ConflitoException("Este pedido ainda está sendo processado. Aguarde um instante.");
        }
        try {
            return ResponseEntity
                    .status(cached.getResponseStatus())
                    .body(objectMapper.readValue(cached.getResponseBody(), responseType));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Resposta idempotente ilegível - Key: " + cached.getIdempotencyKey(), e);
        }
    }

    /**
     * Limpa chaves expiradas do banco de dados.
     */
    @Transactional
    public void cleanupExpiredKeys() {
        idempotencyKeyRepository.deleteExpiredKeys(LocalDateTime.now());
    }

    /**
     * Verifica se uma chave de idempotência já existe.
     */
    public Optional<IdempotencyKeyEntity> findExistingKey(String key, String endpoint) {
        return idempotencyKeyRepository.findByKeyAndEndpoint(key, endpoint, LocalDateTime.now());
    }
}
