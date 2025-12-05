package com.siseg.pagamento.validator;

import com.siseg.pagamento.exception.PagamentoJaReembolsadoException;
import com.siseg.pagamento.model.Pagamento;
import com.siseg.pagamento.model.enumerations.MetodoPagamento;
import com.siseg.pagamento.model.enumerations.StatusPagamento;
import org.springframework.stereotype.Component;

@Component
public class PagamentoValidator {
    
    public void validateReembolsoPossivel(Pagamento pagamento) {
        if (pagamento == null) {
            throw new IllegalArgumentException("Pagamento não pode ser nulo");
        }
        
        if (pagamento.getStatus() == StatusPagamento.REFUNDED) {
            throw new PagamentoJaReembolsadoException("Pagamento já foi reembolsado");
        }
        
        if (pagamento.getStatus() != StatusPagamento.PAID && 
            pagamento.getStatus() != StatusPagamento.AUTHORIZED) {
            throw new IllegalStateException("Apenas pagamentos PAID ou AUTHORIZED podem ser reembolsados");
        }
        
        if (pagamento.getAsaasPaymentId() == null && pagamento.getMetodo() != MetodoPagamento.CASH) {
            throw new IllegalStateException("Pagamento não possui ID do gateway para reembolso");
        }
    }
}


