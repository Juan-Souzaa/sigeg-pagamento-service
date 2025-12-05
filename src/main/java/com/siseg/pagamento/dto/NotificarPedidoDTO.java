package com.siseg.pagamento.dto;

import com.siseg.pagamento.model.enumerations.StatusPagamento;
import lombok.Data;

@Data
public class NotificarPedidoDTO {
    private Long pedidoId;
    private StatusPagamento statusPagamento;
    private String asaasPaymentId;
}


