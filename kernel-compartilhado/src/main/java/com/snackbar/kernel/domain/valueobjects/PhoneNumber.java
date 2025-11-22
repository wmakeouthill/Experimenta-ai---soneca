package com.snackbar.kernel.domain.valueobjects;

import com.snackbar.kernel.domain.exceptions.ValidationException;
import lombok.Value;

import java.util.regex.Pattern;

@Value
public class PhoneNumber {
    String value;
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{10,11}$");
    
    private PhoneNumber(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException("Telefone não pode ser nulo ou vazio");
        }
        String cleanedValue = value.replaceAll("[^0-9]", "");
        if (cleanedValue.length() < 10 || cleanedValue.length() > 11) {
            throw new ValidationException("Telefone deve conter 10 ou 11 dígitos");
        }
        if (!PHONE_PATTERN.matcher(cleanedValue).matches()) {
            throw new ValidationException("Telefone inválido: formato incorreto");
        }
        this.value = cleanedValue;
    }
    
    public static PhoneNumber of(String value) {
        return new PhoneNumber(value);
    }
    
    public String getFormatted() {
        if (value.length() == 11) {
            return String.format("(%s) %s-%s",
                value.substring(0, 2),
                value.substring(2, 7),
                value.substring(7, 11)
            );
        } else {
            return String.format("(%s) %s-%s",
                value.substring(0, 2),
                value.substring(2, 6),
                value.substring(6, 10)
            );
        }
    }
}

