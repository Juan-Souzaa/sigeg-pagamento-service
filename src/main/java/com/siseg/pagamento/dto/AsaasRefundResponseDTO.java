package com.siseg.pagamento.dto;

import lombok.Data;

@Data
public class AsaasRefundResponseDTO {
    private String id;
    private String payment;
    private String value;
    private String status;
    private String transactionReceiptUrl;
    private String dateCreated;
    private String description;
}


