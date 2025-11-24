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

    public FiltroRelatorioTemporalDTO criar(GranularidadeTempo granularidade, String dataReferenciaIso,
            String dataFimIso) {
        LocalDate referencia = parseDataObrigatoria(dataReferenciaIso);
        LocalDate fim = calcularFim(granularidade, referencia, dataFimIso);
        LocalDate inicio = calcularInicio(granularidade, referencia, dataFimIso);
        return new FiltroRelatorioTemporalDTO(granularidade, inicio, fim);
    }

    private LocalDate calcularFim(GranularidadeTempo granularidade, LocalDate referencia, String dataFimIso) {
        if (dataFimIso != null && !dataFimIso.isBlank()) {
            LocalDate fimInformado = parseDataObrigatoria(dataFimIso);
            if (fimInformado.isAfter(referencia)) {
                return fimInformado;
            }
        }
        return calcularFimPorGranularidade(granularidade, referencia);
    }

    private LocalDate calcularFimPorGranularidade(GranularidadeTempo granularidade, LocalDate referencia) {
        return switch (granularidade) {
            case DIA -> referencia.withDayOfMonth(referencia.lengthOfMonth()).plusDays(1);
            case SEMANA -> referencia.withDayOfMonth(referencia.lengthOfMonth()).plusDays(1);
            case MES -> referencia.withMonth(12).withDayOfMonth(31).plusDays(1);
            case TRIMESTRE -> referencia.withMonth(12).withDayOfMonth(31).plusDays(1);
            case SEMESTRE -> referencia.withMonth(12).withDayOfMonth(31).plusDays(1);
            case ANO -> LocalDate.of(referencia.getYear() + 1, 1, 1);
        };
    }

    private LocalDate calcularInicio(GranularidadeTempo granularidade, LocalDate referencia, String dataFimIso) {
        if (dataFimIso != null && !dataFimIso.isBlank()) {
            return referencia;
        }
        return calcularInicioPorGranularidade(granularidade, referencia);
    }

    private LocalDate calcularInicioPorGranularidade(GranularidadeTempo granularidade, LocalDate referencia) {
        return switch (granularidade) {
            case DIA -> referencia.withDayOfMonth(1);
            case SEMANA -> referencia.withDayOfMonth(1);
            case MES -> referencia.withMonth(1).withDayOfMonth(1);
            case TRIMESTRE -> referencia.withMonth(1).withDayOfMonth(1);
            case SEMESTRE -> referencia.withMonth(1).withDayOfMonth(1);
            case ANO -> LocalDate.of(1, 1, 1);
        };
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
