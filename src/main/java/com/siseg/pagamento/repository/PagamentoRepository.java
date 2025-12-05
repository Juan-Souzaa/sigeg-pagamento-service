package com.siseg.pagamento.repository;

import com.siseg.pagamento.model.Pagamento;
import com.siseg.pagamento.model.enumerations.StatusPagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {
    Optional<Pagamento> findByPedidoId(Long pedidoId);
    List<Pagamento> findByStatus(StatusPagamento status);
    Optional<Pagamento> findByAsaasPaymentId(String asaasPaymentId);
}


