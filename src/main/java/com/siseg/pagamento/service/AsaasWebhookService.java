package com.siseg.pagamento.service;

import com.siseg.pagamento.dto.AsaasWebhookDTO;
import com.siseg.pagamento.exception.ResourceNotFoundException;
import com.siseg.pagamento.model.Pagamento;
import com.siseg.pagamento.model.enumerations.StatusPagamento;
import com.siseg.pagamento.repository.PagamentoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.logging.Logger;

@Service
public class AsaasWebhookService {
    
    private static final Logger logger = Logger.getLogger(AsaasWebhookService.class.getName());
    
    @Value("${asaas.webhookSecret}")
    private String webhookSecret;
    
    private final PagamentoRepository pagamentoRepository;
    private final PedidoServiceClient pedidoServiceClient;
    
    public AsaasWebhookService(PagamentoRepository pagamentoRepository, 
                               PedidoServiceClient pedidoServiceClient) {
        this.pagamentoRepository = pagamentoRepository;
        this.pedidoServiceClient = pedidoServiceClient;
    }
    
    public boolean validarAccessToken(String accessToken) {
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            logger.warning("Webhook secret não configurado - webhook será rejeitado");
            return false;
        }
        return accessToken != null && accessToken.equals(webhookSecret);
    }
    
    @Transactional
    public void processarWebhook(AsaasWebhookDTO webhook) {
        if (!isEventoPagamentoValido(webhook)) {
            return;
        }
        
        String asaasPaymentId = webhook.getPayment().getId();
        Pagamento pagamento = buscarPagamentoPorAsaasId(asaasPaymentId);
        
        String evento = webhook.getEvent();
        processarEvento(evento, pagamento, asaasPaymentId);
        
        pagamentoRepository.save(pagamento);
    }
    
    private boolean isEventoPagamentoValido(AsaasWebhookDTO webhook) {
        if (webhook == null || webhook.getEvent() == null || webhook.getPayment() == null) {
            return false;
        }
        String evento = webhook.getEvent();
        return "PAYMENT_RECEIVED".equals(evento) || 
               "PAYMENT_CONFIRMED".equals(evento) || 
               "PAYMENT_REFUSED".equals(evento);
    }
    
    private Pagamento buscarPagamentoPorAsaasId(String asaasPaymentId) {
        return pagamentoRepository.findByAsaasPaymentId(asaasPaymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado: " + asaasPaymentId));
    }
    
    private void processarEvento(String evento, Pagamento pagamento, String asaasPaymentId) {
        if ("PAYMENT_RECEIVED".equals(evento) || "PAYMENT_CONFIRMED".equals(evento)) {
            processarPagamentoConfirmado(pagamento, asaasPaymentId);
        } else if ("PAYMENT_REFUSED".equals(evento)) {
            processarPagamentoRecusado(pagamento, asaasPaymentId);
        }
    }
    
    private void processarPagamentoConfirmado(Pagamento pagamento, String asaasPaymentId) {
        pagamento.setStatus(StatusPagamento.PAID);
        pagamento.setAtualizadoEm(java.time.Instant.now());
        logger.info("Pagamento confirmado via webhook: " + asaasPaymentId + " - Pedido: " + pagamento.getPedidoId());
        
        // Notificar monólito sobre pagamento confirmado
        pedidoServiceClient.notificarPagamentoConfirmado(pagamento.getPedidoId(), asaasPaymentId);
    }
    
    private void processarPagamentoRecusado(Pagamento pagamento, String asaasPaymentId) {
        pagamento.setStatus(StatusPagamento.REFUSED);
        logger.warning("Pagamento recusado via webhook: " + asaasPaymentId + " - Pedido: " + pagamento.getPedidoId());
    }
}





