package com.snackbar.clientes.infrastructure.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.snackbar.clientes.application.ports.GoogleAuthServicePort;
import com.snackbar.clientes.application.ports.GoogleUserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class GoogleAuthServiceAdapter implements GoogleAuthServicePort {

    @Value("${google.client-id:}")
    private String clientId;

    private GoogleIdTokenVerifier getVerifier() {
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    @Override
    public GoogleUserInfo validarTokenGoogle(String googleToken) {
        try {
            GoogleIdToken idToken = getVerifier().verify(googleToken);

            if (idToken == null) {
                throw new IllegalArgumentException("Token do Google inválido");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            String googleId = payload.getSubject();
            String email = payload.getEmail();
            String nome = (String) payload.get("name");
            String fotoUrl = (String) payload.get("picture");
            boolean emailVerificado = Boolean.TRUE.equals(payload.getEmailVerified());

            return new GoogleUserInfo(googleId, email, nome, fotoUrl, emailVerificado);

        } catch (Exception e) {
            throw new IllegalArgumentException("Erro ao validar token do Google: " + e.getMessage(), e);
        }
    }
}
