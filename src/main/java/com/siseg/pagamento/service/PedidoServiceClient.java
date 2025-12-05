package com.siseg.pagamento.service;

import com.siseg.pagamento.dto.NotificarPedidoDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.util.logging.Logger;

@Service
public class PedidoServiceClient {
    
    private static final Logger logger = Logger.getLogger(PedidoServiceClient.class.getName());
    
    private final WebClient webClient;
    private final String serviceKey;
    
    public PedidoServiceClient(@Value("${pedido.service.url}") String pedidoServiceUrl,
                               @Value("${pedido.service.key}") String serviceKey) {
        this.serviceKey = serviceKey;
        this.webClient = WebClient.builder()
                .baseUrl(pedidoServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
    
    public void notificarPagamentoConfirmado(Long pedidoId, String asaasPaymentId) {
        try {
            NotificarPedidoDTO notificacao = new NotificarPedidoDTO();
            notificacao.setPedidoId(pedidoId);
            notificacao.setStatusPagamento(com.siseg.pagamento.model.enumerations.StatusPagamento.PAID);
            notificacao.setAsaasPaymentId(asaasPaymentId);
            
            webClient.post()
                    .uri("/api/pedidos/pagamento-confirmado")
                    .header("X-Service-Key", serviceKey)
                    .bodyValue(notificacao)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
                    
            logger.info("Notificação de pagamento confirmado enviada para pedido: " + pedidoId);
            
        } catch (WebClientException e) {
            logger.warning("Erro ao notificar monólito sobre pagamento confirmado para pedido " + pedidoId + ": " + e.getMessage());
            // Não lança exceção para não interromper o processamento do webhook
        } catch (Exception e) {
            logger.warning("Erro ao notificar monólito sobre pagamento confirmado: " + e.getMessage());
        }
    }
}


