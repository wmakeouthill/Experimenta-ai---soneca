package com.snackbar.pedidos.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.snackbar.pedidos.application.dto.ContaMesaComPixDTO;
import com.snackbar.pedidos.application.dto.ContaMesaDTO;
import com.snackbar.pedidos.application.dto.PixCobrancaCriadaDTO;
import com.snackbar.pedidos.application.usecases.ConsultarContaMesaUseCase;
import com.snackbar.pedidos.application.usecases.FecharContaMesaUseCase;
import com.snackbar.pedidos.domain.entities.StatusPagamento;

@ExtendWith(MockitoExtension.class)
class ContaMesaRestControllerTest {

    @Mock private ConsultarContaMesaUseCase consultarContaMesa;
    @Mock private FecharContaMesaUseCase fecharContaMesa;

    @InjectMocks private ContaMesaRestController controller;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void deveConsultarPreviaDaConta() throws Exception {
        when(consultarContaMesa.executar(eq("token-1"), eq("cliente-1")))
                .thenReturn(new ContaMesaDTO(null, 7, "ABERTA", 5000L, List.of()));

        mockMvc().perform(get("/api/cliente/conta")
                        .param("mesaToken", "token-1")
                        .header("X-Cliente-Id", "cliente-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorCentavos").value(5000));
    }

    @Test
    void deveFecharConta() throws Exception {
        var pix = new PixCobrancaCriadaDTO(
                "corr-1", "txid-1", "payload", "b64", "copia", null, StatusPagamento.AGUARDANDO_PIX);
        when(fecharContaMesa.executar(eq("corr-1"), eq("token-1"), eq("cliente-1")))
                .thenReturn(new ContaMesaComPixDTO(
                        new ContaMesaDTO("conta-1", 7, "ABERTA", 5000L, List.of()), pix));

        mockMvc().perform(post("/api/cliente/conta/fechar")
                        .header("X-Cliente-Id", "cliente-1")
                        .header("X-Correlation-Id", "corr-1")
                        .contentType("application/json")
                        .content("{\"mesaToken\":\"token-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pagamento.copiaECola").value("copia"));
    }
}
