package com.snackbar.orquestrador.exception;

import com.snackbar.kernel.domain.exceptions.BusinessRuleException;
import com.snackbar.kernel.domain.exceptions.ConflitoException;
import com.snackbar.kernel.domain.exceptions.DomainException;
import com.snackbar.kernel.domain.exceptions.RecursoNaoEncontradoException;
import com.snackbar.kernel.domain.exceptions.ValidationException;
import com.snackbar.pedidos.domain.exceptions.MesaNaoEncontradaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(ValidationException ex) {
        Map<String, Object> body = criarRespostaErro(
            HttpStatus.BAD_REQUEST.value(),
            "Erro de Validação",
            ex.getMessage()
        );
        return ResponseEntity.badRequest().body(body);
    }
    
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessRuleException(BusinessRuleException ex) {
        Map<String, Object> body = criarRespostaErro(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "Erro de Regra de Negócio",
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }
    
    // QR code com token inválido/mesa excluída é recurso inexistente, não requisição malformada
    @ExceptionHandler({RecursoNaoEncontradoException.class, MesaNaoEncontradaException.class})
    public ResponseEntity<Map<String, Object>> handleRecursoNaoEncontrado(DomainException ex) {
        Map<String, Object> body = criarRespostaErro(
            HttpStatus.NOT_FOUND.value(),
            "Recurso Não Encontrado",
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // Outro operador já aceitou/rejeitou o mesmo pedido da fila
    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<Map<String, Object>> handleConflitoException(ConflitoException ex) {
        Map<String, Object> body = criarRespostaErro(
            HttpStatus.CONFLICT.value(),
            "Conflito",
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, Object>> handleDomainException(DomainException ex) {
        Map<String, Object> body = criarRespostaErro(
            HttpStatus.BAD_REQUEST.value(),
            "Erro de Domínio",
            ex.getMessage()
        );
        return ResponseEntity.badRequest().body(body);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        Map<String, Object> body = criarRespostaErro(
            HttpStatus.BAD_REQUEST.value(),
            "Erro de Validação",
            "Dados inválidos fornecidos"
        );
        body.put("errors", errors);
        
        return ResponseEntity.badRequest().body(body);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, Object> body = criarRespostaErro(
            HttpStatus.BAD_REQUEST.value(),
            "Requisição Inválida",
            ex.getMessage()
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatchException(TypeMismatchException ex) {
        Class<?> requiredType = ex.getRequiredType();
        String tipoEsperado = "tipo desconhecido";
        if (requiredType != null) {
            String simpleName = requiredType.getSimpleName();
            tipoEsperado = simpleName != null ? simpleName : requiredType.getName();
        }
        
        Object value = ex.getValue();
        String valorRecebido = "null";
        if (value != null) {
            valorRecebido = value.toString();
        }
        
        String mensagem = String.format(
            "Valor inválido para o parâmetro '%s'. Esperado: %s, recebido: %s",
            ex.getPropertyName(),
            tipoEsperado,
            valorRecebido
        );
        
        Map<String, Object> body = criarRespostaErro(
            HttpStatus.BAD_REQUEST.value(),
            "Erro de Conversão de Tipo",
            mensagem
        );
        
        logger.warn("Erro de conversão de tipo: {}", mensagem);
        
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingRequestHeaderException(
            MissingRequestHeaderException ex) {
        String mensagem = String.format(
            "Header obrigatório ausente: %s",
            ex.getHeaderName()
        );

        Map<String, Object> body = criarRespostaErro(
            HttpStatus.BAD_REQUEST.value(),
            "Requisição Inválida",
            mensagem
        );

        logger.warn("Header obrigatório ausente: {}", ex.getHeaderName());

        return ResponseEntity.badRequest().body(body);
    }
    
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex) {
        logger.warn("Corpo da requisição ilegível: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(criarRespostaErro(
            HttpStatus.BAD_REQUEST.value(),
            "Requisição Inválida",
            "Corpo da requisição inválido ou mal formatado."
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        // Exceções do Spring MVC (rota inexistente, método não suportado...) já trazem o status 4xx certo
        if (ex instanceof ErrorResponse erroSpring) {
            int status = erroSpring.getStatusCode().value();
            logger.warn("Requisição rejeitada ({}): {}", status, ex.getMessage());
            if (status == HttpStatus.NOT_FOUND.value()) {
                return ResponseEntity.status(status)
                    .body(criarRespostaErro(status, "Recurso Não Encontrado", "Recurso não encontrado."));
            }
            return ResponseEntity.status(status)
                .body(criarRespostaErro(status, erroSpring.getBody().getTitle(), erroSpring.getBody().getDetail()));
        }

        // Detalhe (classe, SQL, causa) só no log: a resposta é pública
        logger.error("Erro inesperado: ", ex);

        Map<String, Object> body = criarRespostaErro(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Erro Interno do Servidor",
            "Ocorreu um erro inesperado. Tente novamente mais tarde."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
    
    private Map<String, Object> criarRespostaErro(int status, String error, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        return body;
    }
}

