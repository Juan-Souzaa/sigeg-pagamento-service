package com.siseg.pagamento.mapper;

import com.siseg.pagamento.dto.*;
import com.siseg.pagamento.model.enumerations.MetodoPagamento;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class PagamentoMapper {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String BILLING_TYPE_PIX = "PIX";
    private static final String BILLING_TYPE_CREDIT_CARD = "CREDIT_CARD";
    private static final int DIAS_VENCIMENTO = 1;
    
    public AsaasPaymentRequestDTO toAsaasPaymentRequest(
            Long pedidoId,
            BigDecimal valor,
            MetodoPagamento metodo,
            String asaasCustomerId, 
            CartaoCreditoRequestDTO cartaoDTO, 
            ClienteInfoDTO cliente, 
            String cpfCnpj, 
            String remoteIp) {
        AsaasPaymentRequestDTO request = new AsaasPaymentRequestDTO();
        request.setCustomer(asaasCustomerId);
        request.setValue(valor.toString());
        request.setDueDate(LocalDate.now().plusDays(DIAS_VENCIMENTO).format(DATE_FORMATTER));
        request.setDescription("Pedido SIGEG #" + pedidoId);
        request.setExternalReference(pedidoId.toString());
        
        if (metodo == MetodoPagamento.CREDIT_CARD && cartaoDTO != null) {
            request.setBillingType(BILLING_TYPE_CREDIT_CARD);
            request.setCreditCard(toAsaasCreditCardRequest(cartaoDTO));
            if (cliente != null) {
                request.setCreditCardHolderInfo(toAsaasCreditCardHolderInfo(cliente, cpfCnpj));
            }
            if (remoteIp != null && !remoteIp.isEmpty()) {
                request.setRemoteIp(remoteIp);
            }
        } else {
            request.setBillingType(BILLING_TYPE_PIX);
        }
        
        return request;
    }
    
    private AsaasPaymentRequestDTO.CreditCardDTO toAsaasCreditCardRequest(CartaoCreditoRequestDTO cartaoDTO) {
        AsaasPaymentRequestDTO.CreditCardDTO creditCard = new AsaasPaymentRequestDTO.CreditCardDTO();
        creditCard.setHolderName(cartaoDTO.getNomeTitular());
        creditCard.setNumber(cartaoDTO.getNumero());
        
        String[] validadeParts = cartaoDTO.getValidade().split("/");
        creditCard.setExpiryMonth(validadeParts[0]);
        creditCard.setExpiryYear("20" + validadeParts[1]);
        creditCard.setCcv(cartaoDTO.getCvv());
        
        return creditCard;
    }
    
    private AsaasPaymentRequestDTO.CreditCardHolderInfoDTO toAsaasCreditCardHolderInfo(ClienteInfoDTO cliente, String cpfCnpj) {
        AsaasPaymentRequestDTO.CreditCardHolderInfoDTO holderInfo = new AsaasPaymentRequestDTO.CreditCardHolderInfoDTO();
        holderInfo.setName(cliente.getNome());
        holderInfo.setEmail(cliente.getEmail());
        holderInfo.setCpfCnpj(cpfCnpj != null && !cpfCnpj.isEmpty() ? cpfCnpj : null);
        holderInfo.setPostalCode(extrairCepNumerico(cliente.getCep()));
        holderInfo.setAddressNumber(cliente.getAddressNumber());
        holderInfo.setAddressComplement(cliente.getAddressComplement()); 
        holderInfo.setPhone(extrairTelefoneNumerico(cliente.getTelefone()));
        holderInfo.setMobilePhone(extrairTelefoneNumerico(cliente.getTelefone()));
        
        return holderInfo;
    }
    
    private String extrairCepNumerico(String cep) {
        if (cep == null || cep.isEmpty()) {
            return null;
        }
        
        return cep.replaceAll("[^0-9]", "");
    }
    
    private String extrairTelefoneNumerico(String telefone) {
        if (telefone == null) {
            return null;
        }
        return telefone.replaceAll("[^0-9]", "");
    }
    
    public AsaasCustomerRequestDTO toAsaasCustomerRequest(ClienteInfoDTO cliente, String cpfCnpj) {
        AsaasCustomerRequestDTO request = new AsaasCustomerRequestDTO();
        request.setName(cliente.getNome());
        request.setEmail(cliente.getEmail());
        request.setPhone(extrairTelefoneNumerico(cliente.getTelefone()));
        request.setCpfCnpj(cpfCnpj);
        return request;
    }
}



