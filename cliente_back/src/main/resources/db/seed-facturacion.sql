-- Datos de ejemplo para la tabla facturacion (BillingEntity).
-- La app usa customer_id como VARCHAR (string), alineado con customers.id como texto "1", "2", etc.
--
-- Ejecutar contra la misma BD que cliente_back (por defecto internet-db en localhost:5432):
--   psql "postgresql://admin-agent:admin-agent@localhost:5432/internet-db" -f seed-facturacion.sql
--
-- Idempotente: solo inserta si no hay ya facturas para el cliente '1'.

INSERT INTO facturacion (
    customer_id,
    invoice_number,
    total_amount,
    due_date,
    issue_date,
    period_label,
    status,
    previous_balance,
    current_charges,
    discounts,
    interests
)
SELECT * FROM (VALUES
    (
        '1',
        'FAC-2026-001',
        8550.00::numeric,
        (CURRENT_DATE + INTERVAL '15 days')::date,
        (CURRENT_DATE - INTERVAL '5 days')::date,
        '2026-03',
        'DUE',
        0::numeric,
        8000.00::numeric,
        0::numeric,
        550.00::numeric
    ),
    (
        '1',
        'FAC-2026-002',
        9200.00::numeric,
        (CURRENT_DATE + INTERVAL '10 days')::date,
        (CURRENT_DATE - INTERVAL '35 days')::date,
        '2026-02',
        'OVERDUE',
        500.00::numeric,
        8200.00::numeric,
        0::numeric,
        500.00::numeric
    ),
    (
        '1',
        'FAC-2025-12',
        7100.00::numeric,
        (CURRENT_DATE - INTERVAL '60 days')::date,
        (CURRENT_DATE - INTERVAL '90 days')::date,
        '2025-12',
        'PAID',
        0::numeric,
        6800.00::numeric,
        300.00::numeric,
        0::numeric
    )
) AS v(
    customer_id,
    invoice_number,
    total_amount,
    due_date,
    issue_date,
    period_label,
    status,
    previous_balance,
    current_charges,
    discounts,
    interests
)
WHERE NOT EXISTS (
    SELECT 1 FROM facturacion f WHERE f.customer_id = '1' LIMIT 1
);
