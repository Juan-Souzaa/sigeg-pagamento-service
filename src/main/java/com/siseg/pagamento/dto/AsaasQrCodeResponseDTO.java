package com.siseg.pagamento.dto;

import lombok.Data;

@Data
public class AsaasQrCodeResponseDTO {
    private String encodedImage;    // Imagem do QrCode em base64
    private String payload;        // Copia e Cola do QrCode
    private String expirationDate; // Data de expiração do QrCode
    private String description;     // Descrição do QrCode
}


