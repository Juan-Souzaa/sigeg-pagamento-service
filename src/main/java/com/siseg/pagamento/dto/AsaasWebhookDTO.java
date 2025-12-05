package com.siseg.pagamento.dto;

import lombok.Data;

@Data
public class AsaasWebhookDTO {
    private String event;
    private PaymentData payment;
    
    @Data
    public static class PaymentData {
        private String id;
        private String status;
        private String value;
        private String externalReference;
    }
}


