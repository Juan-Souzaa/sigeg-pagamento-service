package com.siseg.pagamento.controller;

import com.siseg.pagamento.dto.*;
import com.siseg.pagamento.service.AsaasWebhookService;
import com.siseg.pagamento.service.PagamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pagamentos")
@Tag(name = "Pagamentos", description = "Operações de pagamento")
public class PagamentoController {
    
    private final PagamentoService pagamentoService;
    private final AsaasWebhookService asaasWebhookService;
    
    public PagamentoController(PagamentoService pagamentoService, AsaasWebhookService asaasWebhookService) {
        this.pagamentoService = pagamentoService;
        this.asaasWebhookService = asaasWebhookService;
    }
    
    @PostMapping
    @Operation(summary = "Criar pagamento para pedido")
    public ResponseEntity<PagamentoResponseDTO> criarPagamento(
            @RequestBody @Valid CriarPagamentoCompletoRequestDTO request,
            HttpServletRequest httpRequest) {
        String remoteIp = getClientIpAddress(httpRequest);
        PagamentoResponseDTO response = pagamentoService.criarPagamento(
                request.getPagamento(), 
                request.getCliente(), 
                remoteIp
        );
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/pedidos/{pedidoId}")
    @Operation(summary = "Buscar pagamento por pedido")
    public ResponseEntity<PagamentoResponseDTO> buscarPagamentoPorPedido(@PathVariable Long pedidoId) {
        PagamentoResponseDTO response = pagamentoService.buscarPagamentoPorPedido(pedidoId);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/webhook")
    @Operation(summary = "Webhook do Asaas para confirmação de pagamento")
    public ResponseEntity<String> webhookAsaas(
            @RequestBody AsaasWebhookDTO webhook,
            @RequestHeader("asaas-access-token") String signature,
            HttpServletRequest request) {
        
        if (!asaasWebhookService.validarAccessToken(signature)) {
            return ResponseEntity.badRequest().body("Assinatura inválida");
        }
        
        asaasWebhookService.processarWebhook(webhook);
        return ResponseEntity.ok("Webhook processado com sucesso");
    }
    
    @PostMapping("/pedidos/{pedidoId}/reembolso")
    @Operation(summary = "Estornar pagamento de um pedido")
    public ResponseEntity<PagamentoResponseDTO> estornarPagamento(
            @PathVariable Long pedidoId,
            @RequestBody @Valid ReembolsoRequestDTO request) {
        PagamentoResponseDTO response = pagamentoService.processarReembolso(pedidoId, request.getMotivo());
        return ResponseEntity.ok(response);
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}


