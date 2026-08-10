-- LOG-020: garante no maximo uma TransportTransaction OPEN por truck.
--
-- Não é UNIQUE(truck_id, status) puro: isso impediria o histórico normal de
-- múltiplas transações COMPLETED (ou CANCELLED) para o mesmo caminhão ao
-- longo do tempo. Um índice único PARCIAL restringe a exclusividade só às
-- linhas com status = 'OPEN' — COMPLETED/CANCELLED nunca colidem entre si
-- nem com essa constraint.
--
-- Esta é a garantia real contra a race condition (duas requisições
-- concorrentes tentando abrir transação pro mesmo truck): o Postgres
-- serializa o INSERT/violação de índice no nível do banco, diferente de um
-- "SELECT existe? -> INSERT" na aplicação, que tem uma janela TOCTOU entre
-- as duas operações. OpenTransportTransactionUseCase ainda faz o SELECT
-- primeiro (evita a viagem ao banco terminar em exceção no caminho comum,
-- sem concorrência real), mas quem garante a invariante é este índice.
CREATE UNIQUE INDEX ux_transport_transactions_truck_open
    ON transport_transactions (truck_id)
    WHERE status = 'OPEN';
