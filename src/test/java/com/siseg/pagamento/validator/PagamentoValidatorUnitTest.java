package com.siseg.pagamento.validator;

import com.siseg.pagamento.exception.PagamentoJaReembolsadoException;
import com.siseg.pagamento.model.Pagamento;
import com.siseg.pagamento.model.enumerations.MetodoPagamento;
import com.siseg.pagamento.model.enumerations.StatusPagamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PagamentoValidatorUnitTest {

    @InjectMocks
    private PagamentoValidator pagamentoValidator;

    private Pagamento pagamento;

    @BeforeEach
    void setUp() {
        pagamento = new Pagamento();
        pagamento.setId(1L);
        pagamento.setValor(new BigDecimal("100.00"));
    }

    @Test
    void deveValidarReembolsoPossivelQuandoPagamentoPaid() {
        pagamento.setStatus(StatusPagamento.PAID);
        pagamento.setMetodo(MetodoPagamento.PIX);
        pagamento.setAsaasPaymentId("pay_123456");

        assertDoesNotThrow(() -> pagamentoValidator.validateReembolsoPossivel(pagamento));
    }

    @Test
    void deveValidarReembolsoPossivelQuandoPagamentoAuthorized() {
        pagamento.setStatus(StatusPagamento.AUTHORIZED);
        pagamento.setMetodo(MetodoPagamento.PIX);
        pagamento.setAsaasPaymentId("pay_123456");

        assertDoesNotThrow(() -> pagamentoValidator.validateReembolsoPossivel(pagamento));
    }

    @Test
    void deveValidarReembolsoPossivelQuandoPagamentoCash() {
        pagamento.setStatus(StatusPagamento.PAID);
        pagamento.setMetodo(MetodoPagamento.CASH);

        assertDoesNotThrow(() -> pagamentoValidator.validateReembolsoPossivel(pagamento));
    }

    @Test
    void deveLancarExcecaoQuandoPagamentoJaReembolsado() {
        pagamento.setStatus(StatusPagamento.REFUNDED);

        assertThrows(PagamentoJaReembolsadoException.class, 
                () -> pagamentoValidator.validateReembolsoPossivel(pagamento));
    }

    @Test
    void deveLancarExcecaoQuandoPagamentoStatusInvalidoParaReembolso() {
        pagamento.setStatus(StatusPagamento.PENDING);

        assertThrows(IllegalStateException.class, 
                () -> pagamentoValidator.validateReembolsoPossivel(pagamento));
    }

    @Test
    void deveLancarExcecaoQuandoPagamentoEletronicoSemAsaasPaymentId() {
        pagamento.setStatus(StatusPagamento.PAID);
        pagamento.setMetodo(MetodoPagamento.PIX);
        pagamento.setAsaasPaymentId(null);

        assertThrows(IllegalStateException.class, 
                () -> pagamentoValidator.validateReembolsoPossivel(pagamento));
    }

    @Test
    void deveLancarExcecaoQuandoPagamentoNulo() {
        assertThrows(IllegalArgumentException.class, 
                () -> pagamentoValidator.validateReembolsoPossivel(null));
    }
}

