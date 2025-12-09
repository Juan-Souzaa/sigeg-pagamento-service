package com.siseg.pagamento.service;

import com.siseg.pagamento.dto.*;
import com.siseg.pagamento.exception.PaymentGatewayException;
import com.siseg.pagamento.exception.ResourceNotFoundException;
import com.siseg.pagamento.model.Pagamento;
import com.siseg.pagamento.model.enumerations.MetodoPagamento;
import com.siseg.pagamento.model.enumerations.StatusPagamento;
import com.siseg.pagamento.repository.PagamentoRepository;
import com.siseg.pagamento.validator.PagamentoValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagamentoServiceUnitTest {

    @Mock
    private PagamentoRepository pagamentoRepository;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private PagamentoValidator pagamentoValidator;

    @Mock
    private AsaasService asaasService;

    @InjectMocks
    private PagamentoService pagamentoService;

    private CriarPagamentoRequestDTO criarPagamentoRequest;
    private ClienteInfoDTO clienteInfo;
    private Pagamento pagamento;
    private PagamentoResponseDTO pagamentoResponseDTO;
    private AsaasPaymentResponseDTO asaasPaymentResponse;
    private AsaasQrCodeResponseDTO asaasQrCodeResponse;

    @BeforeEach
    void setUp() {
        criarPagamentoRequest = new CriarPagamentoRequestDTO();
        criarPagamentoRequest.setPedidoId(1L);
        criarPagamentoRequest.setMetodoPagamento(MetodoPagamento.PIX);
        criarPagamentoRequest.setValor(new BigDecimal("100.00"));
        criarPagamentoRequest.setTroco(null);

        clienteInfo = new ClienteInfoDTO();
        clienteInfo.setId(1L);
        clienteInfo.setNome("Cliente Teste");
        clienteInfo.setEmail("cliente@teste.com");
        clienteInfo.setTelefone("11999999999");
        clienteInfo.setCpfCnpj("12345678900");

        pagamento = new Pagamento();
        pagamento.setId(1L);
        pagamento.setPedidoId(1L);
        pagamento.setMetodo(MetodoPagamento.PIX);
        pagamento.setValor(new BigDecimal("100.00"));
        pagamento.setStatus(StatusPagamento.PENDING);

        pagamentoResponseDTO = new PagamentoResponseDTO();
        pagamentoResponseDTO.setId(1L);
        pagamentoResponseDTO.setPedidoId(1L);
        pagamentoResponseDTO.setStatus(StatusPagamento.PENDING);
        pagamentoResponseDTO.setValor(new BigDecimal("100.00"));

        asaasPaymentResponse = new AsaasPaymentResponseDTO();
        asaasPaymentResponse.setId("pay_123456");
        asaasPaymentResponse.setCustomer("cus_123456");
        asaasPaymentResponse.setStatus("PENDING");

        asaasQrCodeResponse = new AsaasQrCodeResponseDTO();
        asaasQrCodeResponse.setPayload("00020126580014BR.GOV.BCB.PIX");
        asaasQrCodeResponse.setEncodedImage("data:image/png;base64,iVBORw0KGgoAAAANS");
    }

    @Test
    void deveCriarPagamentoPixComSucesso() {
        when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
            Pagamento p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });
        when(modelMapper.map(any(Pagamento.class), eq(PagamentoResponseDTO.class))).thenReturn(pagamentoResponseDTO);
        when(asaasService.buscarOuCriarCliente(any(ClienteInfoDTO.class))).thenReturn("cus_123456");
        when(asaasService.criarPagamentoPix(anyLong(), any(BigDecimal.class), anyString())).thenReturn(asaasPaymentResponse);
        when(asaasService.buscarQrCodePix(anyString())).thenReturn(asaasQrCodeResponse);

        PagamentoResponseDTO result = pagamentoService.criarPagamento(criarPagamentoRequest, clienteInfo, null);

        assertNotNull(result);
        verify(pagamentoRepository, times(1)).save(any(Pagamento.class));
        verify(asaasService, times(1)).criarPagamentoPix(eq(1L), eq(new BigDecimal("100.00")), eq("cus_123456"));
        verify(asaasService, times(1)).buscarQrCodePix("pay_123456");
    }

    @Test
    void deveCriarPagamentoCartaoComSucesso() {
        criarPagamentoRequest.setMetodoPagamento(MetodoPagamento.CREDIT_CARD);
        CartaoCreditoRequestDTO cartaoDTO = new CartaoCreditoRequestDTO();
        cartaoDTO.setNumero("4111111111111111");
        cartaoDTO.setNomeTitular("Cliente Teste");
        cartaoDTO.setValidade("12/25");
        cartaoDTO.setCvv("123");
        criarPagamentoRequest.setCartaoCredito(cartaoDTO);

        // Configurar CEP e número do endereço obrigatórios para pagamento com cartão
        clienteInfo.setCep("01310-100");
        clienteInfo.setAddressNumber("123");

        asaasPaymentResponse.setStatus("CONFIRMED");

        when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
            Pagamento p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });
        when(modelMapper.map(any(Pagamento.class), eq(PagamentoResponseDTO.class))).thenReturn(pagamentoResponseDTO);
        when(asaasService.buscarOuCriarCliente(any(ClienteInfoDTO.class))).thenReturn("cus_123456");
        when(asaasService.criarPagamentoCartao(anyLong(), any(BigDecimal.class), anyString(), any(CartaoCreditoRequestDTO.class), any(ClienteInfoDTO.class), anyString()))
                .thenReturn(asaasPaymentResponse);

        PagamentoResponseDTO result = pagamentoService.criarPagamento(criarPagamentoRequest, clienteInfo, "127.0.0.1");

        assertNotNull(result);
        verify(pagamentoRepository, times(1)).save(any(Pagamento.class));
        verify(asaasService, times(1)).criarPagamentoCartao(eq(1L), eq(new BigDecimal("100.00")), eq("cus_123456"), any(CartaoCreditoRequestDTO.class), any(ClienteInfoDTO.class), eq("127.0.0.1"));
    }

    @Test
    void deveLancarExcecaoQuandoCartaoNaoFornecidoParaPagamentoCartao() {
        criarPagamentoRequest.setMetodoPagamento(MetodoPagamento.CREDIT_CARD);
        criarPagamentoRequest.setCartaoCredito(null);

        assertThrows(IllegalArgumentException.class,
                () -> pagamentoService.criarPagamento(criarPagamentoRequest, clienteInfo, null));
    }

    @Test
    void deveCriarPagamentoDinheiroComSucesso() {
        criarPagamentoRequest.setMetodoPagamento(MetodoPagamento.CASH);

        when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
            Pagamento p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });
        when(modelMapper.map(any(Pagamento.class), eq(PagamentoResponseDTO.class))).thenReturn(pagamentoResponseDTO);

        PagamentoResponseDTO result = pagamentoService.criarPagamento(criarPagamentoRequest, clienteInfo, null);

        assertNotNull(result);
        verify(pagamentoRepository, times(1)).save(any(Pagamento.class));
        verify(asaasService, never()).criarPagamentoPix(anyLong(), any(BigDecimal.class), anyString());
        verify(asaasService, never()).criarPagamentoCartao(anyLong(), any(BigDecimal.class), anyString(), any(CartaoCreditoRequestDTO.class), any(ClienteInfoDTO.class), anyString());
    }

    @Test
    void deveLancarExcecaoQuandoErroNaApiAsaas() {
        when(asaasService.buscarOuCriarCliente(any(ClienteInfoDTO.class)))
                .thenThrow(new PaymentGatewayException("Erro de conexão com o gateway de pagamento"));

        assertThrows(PaymentGatewayException.class,
                () -> pagamentoService.criarPagamento(criarPagamentoRequest, clienteInfo, null));
    }

    @Test
    void deveBuscarPagamentoPorPedidoComSucesso() {
        when(pagamentoRepository.findByPedidoId(1L)).thenReturn(Optional.of(pagamento));
        when(modelMapper.map(any(Pagamento.class), eq(PagamentoResponseDTO.class))).thenReturn(pagamentoResponseDTO);

        PagamentoResponseDTO result = pagamentoService.buscarPagamentoPorPedido(1L);

        assertNotNull(result);
        verify(pagamentoRepository, times(1)).findByPedidoId(1L);
    }

    @Test
    void deveLancarExcecaoQuandoPagamentoNaoEncontrado() {
        when(pagamentoRepository.findByPedidoId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> pagamentoService.buscarPagamentoPorPedido(1L));
    }

    @Test
    void deveProcessarReembolsoComSucesso() {
        pagamento.setStatus(StatusPagamento.PAID);
        pagamento.setAsaasPaymentId("pay_123456");
        pagamento.setValor(new BigDecimal("100.00"));

        AsaasRefundResponseDTO refundResponse = new AsaasRefundResponseDTO();
        refundResponse.setId("refund_123456");
        refundResponse.setValue("100.00");
        refundResponse.setStatus("REFUNDED");

        when(pagamentoRepository.findByPedidoId(1L)).thenReturn(Optional.of(pagamento));
        when(asaasService.estornarPagamento(anyString(), anyString())).thenReturn(refundResponse);
        when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelMapper.map(any(Pagamento.class), eq(PagamentoResponseDTO.class))).thenReturn(pagamentoResponseDTO);

        PagamentoResponseDTO result = pagamentoService.processarReembolso(1L, "Teste de reembolso");

        assertNotNull(result);
        verify(asaasService, times(1)).estornarPagamento("pay_123456", "Teste de reembolso");
        verify(pagamentoRepository, times(1)).save(argThat(p ->
                p.getStatus() == StatusPagamento.REFUNDED));
    }

    @Test
    void deveLancarExcecaoQuandoPagamentoNaoEncontradoParaReembolso() {
        when(pagamentoRepository.findByPedidoId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> pagamentoService.processarReembolso(1L, "Teste"));
    }

    @Test
    void deveLancarExcecaoQuandoErroAoEstornarNoAsaas() {
        pagamento.setStatus(StatusPagamento.PAID);
        pagamento.setAsaasPaymentId("pay_123456");

        when(pagamentoRepository.findByPedidoId(1L)).thenReturn(Optional.of(pagamento));
        when(asaasService.estornarPagamento(anyString(), anyString()))
                .thenThrow(new PaymentGatewayException("Erro ao estornar pagamento"));

        assertThrows(PaymentGatewayException.class,
                () -> pagamentoService.processarReembolso(1L, "Teste"));
    }
}

