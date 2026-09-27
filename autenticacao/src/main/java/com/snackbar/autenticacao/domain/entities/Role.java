package com.snackbar.autenticacao.domain.entities;

import lombok.Getter;

@Getter
public enum Role {
    ADMINISTRADOR("ROLE_ADMINISTRADOR", "Administrador"),
    OPERADOR("ROLE_OPERADOR", "Operador"),
    TOTEM("ROLE_TOTEM", "Totem");
    
    private final String authority;
    private final String descricao;
    
    Role(String authority, String descricao) {
        this.authority = authority;
        this.descricao = descricao;
    }
    
    public static Role fromAuthority(String authority) {
        for (Role role : values()) {
            if (role.authority.equals(authority)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Authority inválida: " + authority);
    }
    
    public boolean isAdministrador() {
        return this == ADMINISTRADOR;
    }
    
    public boolean isOperador() {
        return this == OPERADOR;
    }

    public boolean isTotem() {
        return this == TOTEM;
    }
}

