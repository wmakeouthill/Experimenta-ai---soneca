package com.snackbar.pedidos.application.usecases.relatorios;

import com.snackbar.pedidos.application.dtos.relatorios.FiltroRelatorioTemporalDTO;
import com.snackbar.pedidos.application.dtos.relatorios.GranularidadeTempo;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

@Component
public class FiltroRelatorioTemporalFactory {

    public FiltroRelatorioTemporalDTO criar(GranularidadeTempo granularidade, String dataReferenciaIso, String dataFimIso) {
        LocalDate referencia = parseDataObrigatoria(dataReferenciaIso);
        LocalDate fim = calcularFim(granularidade, referencia, dataFimIso);
        LocalDate inicio = calcularInicio(granularidade, referencia, fim, dataFimIso);
        return new FiltroRelatorioTemporalDTO(granularidade, inicio, fim);
    }

    private LocalDate calcularFim(GranularidadeTempo granularidade, LocalDate referencia, String dataFimIso) {
        if (dataFimIso != null && !dataFimIso.isBlank()) {
            LocalDate fimInformado = parseDataObrigatoria(dataFimIso);
            if (fimInformado.isAfter(referencia)) {
                return fimInformado;
            }
        }
        return granularidade.adicionar(referencia, 1);
    }

    private LocalDate calcularInicio(GranularidadeTempo granularidade, LocalDate referencia, LocalDate fim, String dataFimIso) {
        if (dataFimIso != null && !dataFimIso.isBlank()) {
            return referencia;
        }
        return granularidade.adicionar(fim, -granularidade.bucketsPadrao());
    }

    private LocalDate parseDataObrigatoria(String valor) {
        Objects.requireNonNull(valor, "dataReferencia é obrigatória");
        String trimmed = valor.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("dataReferencia é obrigatória");
        }
        try {
            if (trimmed.length() == 10) {
                return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
            }
            return OffsetDateTime.parse(trimmed).toLocalDate();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Formato de data inválido: " + trimmed, exception);
        }
    }
}

