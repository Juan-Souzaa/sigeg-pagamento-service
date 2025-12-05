package com.siseg.pagamento.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siseg.pagamento.dto.*;
import com.siseg.pagamento.model.enumerations.MetodoPagamento;
import com.siseg.pagamento.model.enumerations.StatusPagamento;
import com.siseg.pagamento.service.AsaasWebhookService;
import com.siseg.pagamento.service.PagamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = PagamentoController.class, excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
class PagamentoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PagamentoService pagamentoService;

    @MockBean
    private AsaasWebhookService asaasWebhookService;

    private CriarPagamentoCompletoRequestDTO criarPagamentoRequest;
    private PagamentoResponseDTO pagamentoResponse;

    @BeforeEach
    void setUp() {
        CriarPagamentoRequestDTO pagamentoRequest = new CriarPagamentoRequestDTO();
        pagamentoRequest.setPedidoId(1L);
        pagamentoRequest.setMetodoPagamento(MetodoPagamento.PIX);
        pagamentoRequest.setValor(new BigDecimal("100.00"));

        ClienteInfoDTO clienteInfo = new ClienteInfoDTO();
        clienteInfo.setId(1L);
        clienteInfo.setNome("Cliente Teste");
        clienteInfo.setEmail("cliente@teste.com");
        clienteInfo.setTelefone("11999999999");
        clienteInfo.setCpfCnpj("12345678900");

        criarPagamentoRequest = new CriarPagamentoCompletoRequestDTO();
        criarPagamentoRequest.setPagamento(pagamentoRequest);
        criarPagamentoRequest.setCliente(clienteInfo);

        pagamentoResponse = new PagamentoResponseDTO();
        pagamentoResponse.setId(1L);
        pagamentoResponse.setPedidoId(1L);
        pagamentoResponse.setStatus(StatusPagamento.PENDING);
        pagamentoResponse.setValor(new BigDecimal("100.00"));
    }

    @Test
    void deveCriarPagamentoComSucesso() throws Exception {
        when(pagamentoService.criarPagamento(any(), any(), any())).thenReturn(pagamentoResponse);

        mockMvc.perform(post("/api/pagamentos")
                        .with(SecurityMockMvcRequestPostProcessors.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criarPagamentoRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.pedidoId").value(1L))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(pagamentoService, times(1)).criarPagamento(any(), any(), any());
    }

    @Test
    void deveBuscarPagamentoPorPedidoComSucesso() throws Exception {
        when(pagamentoService.buscarPagamentoPorPedido(1L)).thenReturn(pagamentoResponse);

        mockMvc.perform(get("/api/pagamentos/pedidos/1")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.pedidoId").value(1L));

        verify(pagamentoService, times(1)).buscarPagamentoPorPedido(1L);
    }

    @Test
    void deveProcessarWebhookComSucesso() throws Exception {
        when(asaasWebhookService.validarAssinatura(anyString(), anyString())).thenReturn(true);
        doNothing().when(asaasWebhookService).processarWebhook(any());

        AsaasWebhookDTO webhook = new AsaasWebhookDTO();
        webhook.setEvent("PAYMENT_RECEIVED");
        AsaasWebhookDTO.PaymentData paymentData = new AsaasWebhookDTO.PaymentData();
        paymentData.setId("pay_123456");
        webhook.setPayment(paymentData);

        mockMvc.perform(post("/api/pagamentos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", "test-signature")
                        .content(objectMapper.writeValueAsString(webhook)))
                .andExpect(status().isOk());

        verify(asaasWebhookService, times(1)).validarAssinatura(anyString(), anyString());
        verify(asaasWebhookService, times(1)).processarWebhook(any());
    }

    @Test
    void deveProcessarReembolsoComSucesso() throws Exception {
        pagamentoResponse.setStatus(StatusPagamento.REFUNDED);
        when(pagamentoService.processarReembolso(1L, "Teste")).thenReturn(pagamentoResponse);

        ReembolsoRequestDTO request = new ReembolsoRequestDTO();
        request.setMotivo("Teste");

        mockMvc.perform(post("/api/pagamentos/pedidos/1/reembolso")
                        .with(SecurityMockMvcRequestPostProcessors.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        verify(pagamentoService, times(1)).processarReembolso(1L, "Teste");
    }
}

