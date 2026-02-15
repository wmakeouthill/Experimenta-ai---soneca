package com.snackbar.autenticacao.infrastructure.config;

import com.snackbar.autenticacao.domain.entities.Role;
import com.snackbar.autenticacao.domain.entities.Usuario;
import com.snackbar.autenticacao.domain.ports.UsuarioRepositoryPort;
import com.snackbar.autenticacao.domain.services.SenhaService;
import com.snackbar.autenticacao.domain.valueobjects.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class UsuarioInicialConfig implements CommandLineRunner {

    private final UsuarioRepositoryPort usuarioRepository;
    private final SenhaService senhaService;

    @Override
    public void run(String... args) {
        criarUsuarioAdminSeNaoExistir();
    }

    @SuppressWarnings("null") // Usuario.criar() nunca retorna null, repository.salvar() nunca retorna null
    private void criarUsuarioAdminSeNaoExistir() {
        try {
            if (usuarioRepository.count() > 0) {
                log.info("Já existem usuários no banco; seed do administrador inicial (admin@snackbar.com) ignorado.");
                return;
            }

            Email emailAdmin = Email.of("admin@snackbar.com");

            if (usuarioRepository.existePorEmail(emailAdmin)) {
                log.info("Usuário administrador inicial já existe");
                return;
            }

            var senhaHash = senhaService.criarSenhaComHash("admin123");
            Usuario admin = Usuario.criar(
                    "Admin",
                    emailAdmin,
                    senhaHash,
                    Role.ADMINISTRADOR);

            usuarioRepository.salvar(admin);
            log.info("Usuário administrador inicial criado com sucesso");
            log.info("Email: admin@snackbar.com | Senha: admin123");

        } catch (Exception e) {
            log.error("Erro ao criar usuário administrador inicial", e);
        }
    }
}
