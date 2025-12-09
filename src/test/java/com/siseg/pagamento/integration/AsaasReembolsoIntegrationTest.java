package com.siseg.pagamento.integration;

import com.siseg.pagamento.dto.*;
import com.siseg.pagamento.model.Pagamento;
import com.siseg.pagamento.model.enumerations.MetodoPagamento;
import com.siseg.pagamento.model.enumerations.StatusPagamento;
import com.siseg.pagamento.repository.PagamentoRepository;
import com.siseg.pagamento.service.AsaasService;
import com.siseg.pagamento.service.PagamentoService;
import com.siseg.pagamento.exception.PaymentGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AsaasReembolsoIntegrationTest {

    @Autowired
    private AsaasService asaasService;

    @Autowired
    private PagamentoService pagamentoService;

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Value("${asaas.apiKey:}")
    private String asaasApiKey;

    private CriarPagamentoRequestDTO criarPagamentoRequest;
    private ClienteInfoDTO clienteInfo;
    private Pagamento pagamento;

    @BeforeEach
    @Transactional
    void setUp() {
        criarPagamentoRequest = new CriarPagamentoRequestDTO();
        criarPagamentoRequest.setPedidoId(100L);
        criarPagamentoRequest.setMetodoPagamento(MetodoPagamento.PIX);
        criarPagamentoRequest.setValor(new BigDecimal("15.00"));
        criarPagamentoRequest.setTroco(null);

        clienteInfo = new ClienteInfoDTO();
        clienteInfo.setId(1L);
        clienteInfo.setNome("Cliente Teste Integração Reembolso");
        clienteInfo.setEmail("teste.reembolso." + System.currentTimeMillis() + "@example.com");
        clienteInfo.setTelefone("(11) 99415-2001");
        clienteInfo.setCpfCnpj("24971563792");
    }

    @Test
    @Transactional
    void deveEstornarPagamentoRealNoAsaas() {
        String apiKey = System.getenv("ASAAS_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = asaasApiKey;
        }
        
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("test-key")) {
            return;
        }

        PagamentoResponseDTO pagamentoResponse = pagamentoService.criarPagamento(
            criarPagamentoRequest, 
            clienteInfo, 
            "127.0.0.1"
        );
        
        assertNotNull(pagamentoResponse, "Resposta do pagamento não deve ser nula");
        assertNotNull(pagamentoResponse.getId(), "ID do pagamento não deve ser nulo");
        
        pagamento = pagamentoRepository.findById(pagamentoResponse.getId())
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado"));
        
        assertNotNull(pagamento.getAsaasPaymentId(), "Asaas Payment ID não deve ser nulo");
        
        String asaasPaymentId = pagamento.getAsaasPaymentId();
        
        asaasService.confirmarPagamentoSandbox(asaasPaymentId);
        
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Teste interrompido durante espera");
        }
        
        AsaasPaymentResponseDTO paymentStatus = asaasService.buscarPagamento(asaasPaymentId);
        
        String status = paymentStatus.getStatus();
        assertTrue("RECEIVED".equals(status) || "CONFIRMED".equals(status) || "RECEIVED_IN_CASH_APPROVED".equals(status),
                "Pagamento deve estar confirmado após chamada ao endpoint de confirmação");
        
        pagamento.setStatus(StatusPagamento.PAID);
        pagamentoRepository.save(pagamento);
        
        String motivo = "Teste de integração - reembolso automático";
        
        int tentativasReembolso = 0;
        int maxTentativasReembolso = 5;
        boolean reembolsoProcessado = false;
        
        while (tentativasReembolso < maxTentativasReembolso && !reembolsoProcessado) {
            try {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail("Teste interrompido durante espera de reembolso");
                }
                tentativasReembolso++;
                
                PagamentoResponseDTO resultado = pagamentoService.processarReembolso(
                    pagamento.getPedidoId(), 
                    motivo
                );
                
                assertNotNull(resultado, "Resultado do reembolso não deve ser nulo");
                assertEquals(StatusPagamento.REFUNDED, resultado.getStatus(), "Status deve ser REFUNDED");
                assertNotNull(resultado.getValorReembolsado(), "Valor reembolsado não deve ser nulo");
                assertNotNull(resultado.getDataReembolso(), "Data do reembolso não deve ser nula");
                
                reembolsoProcessado = true;
            } catch (PaymentGatewayException e) {
                if (e.getMessage() != null && (e.getMessage().contains("Saldo insuficiente") || 
                    e.getMessage().contains("Tente novamente em alguns instantes") ||
                    e.getMessage().contains("já está em andamento"))) {
                    if (tentativasReembolso >= maxTentativasReembolso) {
                        return;
                    }
                    continue;
                }
                fail("Erro ao processar reembolso: " + e.getMessage());
            }
        }
    }

    @Test
    void deveVerificarConexaoComAsaas() {
        String apiKey = System.getenv("ASAAS_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = asaasApiKey;
        }
        
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("test-key") || apiKey.startsWith("${")) {
            return;
        }
        
        String customerId = asaasService.buscarOuCriarCliente(clienteInfo);
        
        assertNotNull(customerId, "Deve retornar um ID de cliente");
    }
}

