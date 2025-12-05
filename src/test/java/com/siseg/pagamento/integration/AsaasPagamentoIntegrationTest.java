package com.siseg.pagamento.integration;

import com.siseg.pagamento.dto.*;
import com.siseg.pagamento.model.Pagamento;
import com.siseg.pagamento.model.enumerations.MetodoPagamento;
import com.siseg.pagamento.model.enumerations.StatusPagamento;
import com.siseg.pagamento.repository.PagamentoRepository;
import com.siseg.pagamento.service.AsaasService;
import com.siseg.pagamento.service.PagamentoService;
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
class AsaasPagamentoIntegrationTest {

    @Autowired
    private AsaasService asaasService;

    @Autowired
    private PagamentoService pagamentoService;

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Value("${asaas.apiKey:}")
    private String asaasApiKey;

    private CriarPagamentoRequestDTO criarPagamentoRequestPix;
    private CriarPagamentoRequestDTO criarPagamentoRequestCartao;
    private ClienteInfoDTO clienteInfo;

    @BeforeEach
    @Transactional
    void setUp() {
        criarPagamentoRequestPix = new CriarPagamentoRequestDTO();
        criarPagamentoRequestPix.setPedidoId(1L);
        criarPagamentoRequestPix.setMetodoPagamento(MetodoPagamento.PIX);
        criarPagamentoRequestPix.setValor(new BigDecimal("25.00"));
        criarPagamentoRequestPix.setTroco(null);

        criarPagamentoRequestCartao = new CriarPagamentoRequestDTO();
        criarPagamentoRequestCartao.setPedidoId(2L);
        criarPagamentoRequestCartao.setMetodoPagamento(MetodoPagamento.CREDIT_CARD);
        criarPagamentoRequestCartao.setValor(new BigDecimal("35.00"));
        criarPagamentoRequestCartao.setTroco(null);

        clienteInfo = new ClienteInfoDTO();
        clienteInfo.setId(1L);
        clienteInfo.setNome("Cliente Teste Pagamento");
        clienteInfo.setEmail("teste.pagamento@example.com");
        clienteInfo.setTelefone("(11) 99415-2001");
        clienteInfo.setCpfCnpj("12345678900");
    }

    @Test
    void deveCriarPagamentoPixRealNoAsaas() {
        String apiKey = System.getenv("ASAAS_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = asaasApiKey;
        }
        
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("test-key")) {
            return;
        }

        PagamentoResponseDTO response = pagamentoService.criarPagamento(criarPagamentoRequestPix, clienteInfo, "127.0.0.1");
        
        assertNotNull(response, "Resposta do pagamento não deve ser nula");
        assertNotNull(response.getId(), "ID do pagamento não deve ser nulo");
        assertEquals(MetodoPagamento.PIX, response.getMetodo(), "Método deve ser PIX");
        assertEquals(new BigDecimal("25.00"), response.getValor(), "Valor deve ser 25.00");
        assertNotNull(response.getQrCode(), "QR Code não deve ser nulo");
        assertNotNull(response.getQrCodeImageUrl(), "URL da imagem do QR Code não deve ser nula");
        
        Pagamento pagamento = pagamentoRepository.findById(response.getId())
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado"));
        
        assertNotNull(pagamento.getAsaasPaymentId(), "Asaas Payment ID não deve ser nulo");
        
        AsaasPaymentResponseDTO asaasPayment = asaasService.buscarPagamento(pagamento.getAsaasPaymentId());
        assertNotNull(asaasPayment, "Pagamento deve existir no Asaas");
        assertEquals("PIX", asaasPayment.getBillingType(), "Tipo de pagamento deve ser PIX");
        assertTrue("25.00".equals(asaasPayment.getValue()) || "25".equals(asaasPayment.getValue()), 
            "Valor no Asaas deve ser 25.00 ou 25");
    }

    @Test
    @Transactional
    void deveCriarPagamentoCartaoRealNoAsaas() {
        String apiKey = System.getenv("ASAAS_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = asaasApiKey;
        }
        
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("test-key")) {
            return;
        }

        CartaoCreditoRequestDTO cartaoDTO = new CartaoCreditoRequestDTO();
        cartaoDTO.setNumero("4111111111111111");
        cartaoDTO.setNomeTitular("CLIENTE TESTE");
        cartaoDTO.setValidade("12/25");
        cartaoDTO.setCvv("123");
        criarPagamentoRequestCartao.setCartaoCredito(cartaoDTO);

        PagamentoResponseDTO response = pagamentoService.criarPagamento(
            criarPagamentoRequestCartao, 
            clienteInfo, 
            "127.0.0.1"
        );
        
        assertNotNull(response, "Resposta do pagamento não deve ser nula");
        assertNotNull(response.getId(), "ID do pagamento não deve ser nulo");
        assertEquals(MetodoPagamento.CREDIT_CARD, response.getMetodo(), "Método deve ser CREDIT_CARD");
        assertEquals(new BigDecimal("35.00"), response.getValor(), "Valor deve ser 35.00");
        
        Pagamento pagamento = pagamentoRepository.findById(response.getId())
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado"));
        
        if (pagamento.getAsaasPaymentId() != null && pagamento.getAsaasPaymentId().startsWith("pay_")) {
            AsaasPaymentResponseDTO asaasPayment = asaasService.buscarPagamento(pagamento.getAsaasPaymentId());
            assertNotNull(asaasPayment, "Pagamento deve existir no Asaas");
            assertEquals("CREDIT_CARD", asaasPayment.getBillingType(), "Tipo de pagamento deve ser CREDIT_CARD");
            assertTrue("35.00".equals(asaasPayment.getValue()) || "35".equals(asaasPayment.getValue()), 
                "Valor no Asaas deve ser 35.00 ou 35");
        }
    }

    @Test
    void deveCriarPagamentoPixViaAsaasService() {
        String apiKey = System.getenv("ASAAS_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = asaasApiKey;
        }
        
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("test-key")) {
            return;
        }

        String asaasCustomerId = asaasService.buscarOuCriarCliente(clienteInfo);
        assertNotNull(asaasCustomerId, "Customer ID não deve ser nulo");

        AsaasPaymentResponseDTO response = asaasService.criarPagamentoPix(
            1L, 
            new BigDecimal("25.00"), 
            asaasCustomerId
        );
        
        assertNotNull(response, "Resposta do Asaas não deve ser nula");
        assertNotNull(response.getId(), "ID do pagamento não deve ser nulo");
        assertEquals("PIX", response.getBillingType(), "Tipo de pagamento deve ser PIX");
        assertTrue("25.00".equals(response.getValue()) || "25".equals(response.getValue()), 
            "Valor deve ser 25.00 ou 25");
        
        AsaasPaymentResponseDTO paymentVerification = asaasService.buscarPagamento(response.getId());
        assertNotNull(paymentVerification, "Pagamento deve existir no Asaas");
        assertEquals(response.getId(), paymentVerification.getId(), "IDs devem ser iguais");
    }
}

