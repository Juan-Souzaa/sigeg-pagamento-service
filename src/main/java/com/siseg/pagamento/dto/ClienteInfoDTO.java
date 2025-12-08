package com.siseg.pagamento.dto;

import lombok.Data;

@Data
public class ClienteInfoDTO {
    private Long id;
    private String nome;
    private String email;
    private String telefone;
    private String cpfCnpj;
    private String cep; 
    private String addressNumber; 
    private String addressComplement; 
}



