package com.siseg.pagamento.service;

import com.siseg.pagamento.dto.*;
import com.siseg.pagamento.exception.PaymentGatewayException;
import com.siseg.pagamento.exception.ResourceNotFoundException;
import com.siseg.pagamento.model.Pagamento;
import com.siseg.pagamento.model.enumerations.MetodoPagamento;
import com.siseg.pagamento.model.enumerations.StatusPagamento;
import com.siseg.pagamento.repository.PagamentoRepository;
import com.siseg.pagamento.validator.PagamentoValidator;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.logging.Logger;

@Service
public class PagamentoService {
    
    private static final Logger logger = Logger.getLogger(PagamentoService.class.getName());
    
    private final PagamentoRepository pagamentoRepository;
    private final ModelMapper modelMapper;
    private final PagamentoValidator pagamentoValidator;
    private final AsaasService asaasService;
    
    public PagamentoService(PagamentoRepository pagamentoRepository, 
                           ModelMapper modelMapper,
                           PagamentoValidator pagamentoValidator,
                           AsaasService asaasService) {
        this.pagamentoRepository = pagamentoRepository;
        this.modelMapper = modelMapper;
        this.pagamentoValidator = pagamentoValidator;
        this.asaasService = asaasService;
    }
    
    @Transactional
    public PagamentoResponseDTO criarPagamento(CriarPagamentoRequestDTO request, ClienteInfoDTO clienteInfo, String remoteIp) {
        Pagamento pagamento = criarPagamentoBasico(request);
        
        if (request.getMetodoPagamento() == MetodoPagamento.PIX) {
            processarPagamentoPix(pagamento, clienteInfo);
        } else if (request.getMetodoPagamento() == MetodoPagamento.CREDIT_CARD) {
            if (request.getCartaoCredito() == null) {
                throw new IllegalArgumentException("Dados do cartão são obrigatórios para pagamento com cartão de crédito");
            }
            processarPagamentoCartao(pagamento, request.getCartaoCredito(), clienteInfo, remoteIp);
        } else {
            processarPagamentoDinheiro(pagamento);
        }
        
        Pagamento saved = pagamentoRepository.save(pagamento);
        
        PagamentoResponseDTO response = modelMapper.map(saved, PagamentoResponseDTO.class);
        response.setPedidoId(saved.getPedidoId());
        return response;
    }
    
    private Pagamento criarPagamentoBasico(CriarPagamentoRequestDTO request) {
        Pagamento pagamento = new Pagamento();
        pagamento.setPedidoId(request.getPedidoId());
        pagamento.setMetodo(request.getMetodoPagamento());
        pagamento.setValor(request.getValor());
        pagamento.setTroco(request.getTroco());
        pagamento.setStatus(StatusPagamento.PENDING);
        return pagamento;
    }
    
    private void processarPagamentoDinheiro(Pagamento pagamento) {
        pagamento.setStatus(StatusPagamento.PENDING);
    }
    
    private void processarPagamentoPix(Pagamento pagamento, ClienteInfoDTO clienteInfo) {
        try {
            String asaasCustomerId = asaasService.buscarOuCriarCliente(clienteInfo);
            AsaasPaymentResponseDTO response = asaasService.criarPagamentoPix(
                    pagamento.getPedidoId(), 
                    pagamento.getValor(), 
                    asaasCustomerId
            );
            
            validarRespostaAsaas(response);
            atualizarPagamentoComRespostaAsaas(pagamento, asaasCustomerId, response);
            atualizarPagamentoComQrCode(pagamento, response.getId());
            
        } catch (org.springframework.web.reactive.function.client.WebClientException e) {
            logger.severe("Erro de conexão com API Asaas: " + e.getMessage());
            throw new PaymentGatewayException("Erro de conexão com o gateway de pagamento. Verifique sua conexão com a internet.", e);
        } catch (Exception e) {
            logger.severe("Erro ao criar pagamento PIX: " + e.getMessage());
            throw new PaymentGatewayException("Erro ao processar pagamento PIX: " + e.getMessage());
        }
    }
    
    private void processarPagamentoCartao(Pagamento pagamento, CartaoCreditoRequestDTO cartaoDTO, ClienteInfoDTO clienteInfo, String remoteIp) {
        try {
            String asaasCustomerId = asaasService.buscarOuCriarCliente(clienteInfo);
            AsaasPaymentResponseDTO response = asaasService.criarPagamentoCartao(
                    pagamento.getPedidoId(),
                    pagamento.getValor(),
                    asaasCustomerId,
                    cartaoDTO,
                    clienteInfo,
                    remoteIp
            );
            
            validarRespostaAsaas(response);
            atualizarPagamentoComRespostaAsaas(pagamento, asaasCustomerId, response);
            
            if (response.getStatus() != null && "CONFIRMED".equals(response.getStatus())) {
                pagamento.setStatus(StatusPagamento.AUTHORIZED);
            } else {
                pagamento.setStatus(StatusPagamento.PENDING);
            }
            
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            logger.severe("Erro do Asaas (HTTP " + e.getStatusCode() + "): " + e.getResponseBodyAsString());
            throw new PaymentGatewayException("Erro ao processar pagamento: " + e.getResponseBodyAsString(), e);
        } catch (org.springframework.web.reactive.function.client.WebClientException e) {
            logger.severe("Erro de conexão com API Asaas: " + e.getMessage());
            throw new PaymentGatewayException("Erro de conexão com o gateway de pagamento. Verifique sua conexão com a internet.", e);
        } catch (Exception e) {
            logger.severe("Erro ao criar pagamento com cartão: " + e.getMessage());
            throw new PaymentGatewayException("Erro ao processar pagamento com cartão de crédito: " + e.getMessage());
        }
    }
    
    private void validarRespostaAsaas(AsaasPaymentResponseDTO response) {
        if (response == null) {
            throw new PaymentGatewayException("Resposta nula da API Asaas");
        }
    }
    
    private void atualizarPagamentoComRespostaAsaas(Pagamento pagamento, String asaasCustomerId, AsaasPaymentResponseDTO response) {
        pagamento.setAsaasPaymentId(response.getId());
        pagamento.setAsaasCustomerId(asaasCustomerId);
        pagamento.setStatus(StatusPagamento.AUTHORIZED);
    }
    
    private void atualizarPagamentoComQrCode(Pagamento pagamento, String asaasPaymentId) {
        try {
            AsaasQrCodeResponseDTO qrCodeResponse = asaasService.buscarQrCodePix(asaasPaymentId);
            if (qrCodeResponse != null) {
                pagamento.setQrCode(qrCodeResponse.getPayload());
                pagamento.setQrCodeImageUrl(qrCodeResponse.getEncodedImage());
            }
        } catch (Exception e) {
            logger.severe("Erro ao obter QR Code PIX: " + e.getMessage());
        }
    }
    
    @Transactional
    public PagamentoResponseDTO buscarPagamentoPorPedido(Long pedidoId) {
        Pagamento pagamento = buscarPagamentoPorPedidoId(pedidoId);
        
        if (pagamento.getMetodo() == MetodoPagamento.PIX && 
            pagamento.getStatus() != StatusPagamento.PAID && 
            pagamento.getStatus() != StatusPagamento.REFUNDED &&
            pagamento.getAsaasPaymentId() != null) {
            sincronizarStatusComAsaas(pagamento);
        }
        
        PagamentoResponseDTO response = modelMapper.map(pagamento, PagamentoResponseDTO.class);
        response.setPedidoId(pagamento.getPedidoId());
        return response;
    }
    
    private void sincronizarStatusComAsaas(Pagamento pagamento) {
        try {
            AsaasPaymentResponseDTO asaasResponse = asaasService.buscarPagamento(pagamento.getAsaasPaymentId());
            
            if (asaasResponse != null && asaasResponse.getStatus() != null) {
                String asaasStatus = asaasResponse.getStatus();
                
                if ("CONFIRMED".equals(asaasStatus) || "RECEIVED".equals(asaasStatus)) {
                    pagamento.setStatus(StatusPagamento.PAID);
                    pagamento.setAtualizadoEm(java.time.Instant.now());
                    logger.info("Pagamento PIX sincronizado e confirmado: " + pagamento.getAsaasPaymentId() + " - Pedido: " + pagamento.getPedidoId());
                } else if ("REFUSED".equals(asaasStatus) || "OVERDUE".equals(asaasStatus)) {
                    pagamento.setStatus(StatusPagamento.REFUSED);
                    pagamento.setAtualizadoEm(java.time.Instant.now());
                    logger.info("Pagamento PIX recusado/vencido: " + pagamento.getAsaasPaymentId());
                }
                
                pagamentoRepository.save(pagamento);
            }
        } catch (Exception e) {
            logger.warning("Erro ao sincronizar status com Asaas para pagamento " + pagamento.getId() + ": " + e.getMessage());
        }
    }
    
    private Pagamento buscarPagamentoPorPedidoId(Long pedidoId) {
        return pagamentoRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado para o pedido: " + pedidoId));
    }
    
    @Transactional
    public PagamentoResponseDTO processarReembolso(Long pedidoId, String motivo) {
        Pagamento pagamento = buscarPagamentoPorPedidoId(pedidoId);
        
        pagamentoValidator.validateReembolsoPossivel(pagamento);
        
        if (pagamento.getMetodo() == MetodoPagamento.CASH) {
            processarReembolsoDinheiro(pagamento, motivo);
        } else {
            processarReembolsoEletronico(pagamento, motivo);
        }
        
        Pagamento saved = pagamentoRepository.save(pagamento);
        
        logger.info("Reembolso processado para pedido " + pedidoId + " - Valor: R$ " + pagamento.getValorReembolsado());
        
        PagamentoResponseDTO response = modelMapper.map(saved, PagamentoResponseDTO.class);
        response.setPedidoId(saved.getPedidoId());
        return response;
    }
    
    private void processarReembolsoDinheiro(Pagamento pagamento, String motivo) {
        pagamento.setStatus(StatusPagamento.REFUNDED);
        pagamento.setValorReembolsado(pagamento.getValor());
        pagamento.setDataReembolso(java.time.Instant.now());
        pagamento.setAtualizadoEm(java.time.Instant.now());
        
        logger.info("Reembolso de dinheiro processado - Motivo: " + motivo);
    }
    
    private void processarReembolsoEletronico(Pagamento pagamento, String motivo) {
        try {
            String descricao = motivo != null ? motivo : "Reembolso de pedido cancelado";
            AsaasRefundResponseDTO refundResponse = asaasService.estornarPagamento(
                pagamento.getAsaasPaymentId(), 
                descricao
            );
            
            atualizarPagamentoComReembolso(pagamento, refundResponse);
            
        } catch (org.springframework.web.reactive.function.client.WebClientException e) {
            logger.severe("Erro de conexão ao processar reembolso: " + e.getMessage());
            throw new PaymentGatewayException("Erro de conexão com o gateway de pagamento ao processar reembolso.", e);
        } catch (Exception e) {
            logger.severe("Erro ao processar reembolso: " + e.getMessage());
            throw new PaymentGatewayException("Erro ao processar reembolso: " + e.getMessage());
        }
    }
    
    private void atualizarPagamentoComReembolso(Pagamento pagamento, AsaasRefundResponseDTO refundResponse) {
        pagamento.setStatus(StatusPagamento.REFUNDED);
        pagamento.setValorReembolsado(new java.math.BigDecimal(refundResponse.getValue()));
        pagamento.setDataReembolso(java.time.Instant.now());
        pagamento.setAsaasRefundId(refundResponse.getId());
        pagamento.setAtualizadoEm(java.time.Instant.now());
    }
}


