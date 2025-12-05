-- Migração V1: Tabela de pagamentos
CREATE TABLE pagamentos (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    pedido_id BIGINT NOT NULL UNIQUE,
    metodo VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    valor DECIMAL(10,2) NOT NULL,
    troco DECIMAL(10,2),
    qr_code TEXT,
    qr_code_image_url LONGTEXT,
    asaas_payment_id VARCHAR(100),
    asaas_customer_id VARCHAR(100),
    valor_reembolsado DECIMAL(10,2),
    data_reembolso TIMESTAMP,
    asaas_refund_id VARCHAR(100),
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP
);

CREATE INDEX idx_pagamentos_pedido ON pagamentos(pedido_id);
CREATE INDEX idx_pagamentos_status ON pagamentos(status);
CREATE INDEX idx_pagamentos_asaas_id ON pagamentos(asaas_payment_id);

