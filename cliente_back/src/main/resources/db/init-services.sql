-- Tabla services según ServicesEntity (JPA usa snake_case por defecto)
-- customer_id es el puntero (FK) a customers.id
CREATE TABLE IF NOT EXISTS services (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT REFERENCES customers(id),
    service_name VARCHAR(255) NOT NULL,
    service_type VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    base_price NUMERIC(10, 2),
    discount NUMERIC(10, 2),
    discount_percentage NUMERIC(5, 2),
    start_date DATE,
    billing_day INTEGER,
    status VARCHAR(50),
    promo_expiration VARCHAR(100),
    promo_end_date DATE
);

ALTER TABLE services ADD COLUMN IF NOT EXISTS customer_id BIGINT;
ALTER TABLE services ADD COLUMN IF NOT EXISTS discount_percentage NUMERIC(5, 2);
ALTER TABLE services ADD COLUMN IF NOT EXISTS promo_end_date DATE;
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_services_customer') THEN
    ALTER TABLE services ADD CONSTRAINT fk_services_customer FOREIGN KEY (customer_id) REFERENCES customers(id);
  END IF;
END $$;

INSERT INTO services (id, customer_id, service_name, service_type, address, base_price, discount, discount_percentage, start_date, billing_day, status, promo_expiration, promo_end_date)
VALUES
    (1, 1, 'Internet Fibra 100', 'Internet', 'Av. Corrientes 1234, CABA', 8999.00, 500.00, 10.00, '2024-01-15', 15, 'Activo', '2025-06-30', '2025-06-30'),
    (2, 1, 'Pack Triple', 'Internet', 'Córdoba 567, CABA', 12999.00, 1500.00, 15.00, '2024-03-01', 1, 'Activo', '2025-12-31', '2025-12-31'),
    (3, 1, 'TV HD', 'TV', 'Av. Santa Fe 890, CABA', 3499.00, 0, NULL, '2023-11-20', 20, 'Activo', NULL, NULL),
    (4, 1, 'Línea Fija', 'Telefonía', 'Lavalle 200, CABA', 1899.00, 200.00, 20.00, '2024-02-10', 5, 'Activo', '2025-02-28', '2025-02-28'),
    (5, 1, 'Internet + TV', 'Internet', 'Cabildo 1500, CABA', 7999.00, 1000.00, 12.50, '2024-05-01', 10, 'Activo', '2025-08-15', '2025-08-15'),
    (6, 1, 'Cable Básico', 'TV', 'Av. Libertador 3000, CABA', 2499.00, 0, NULL, '2023-08-01', 1, 'Suspendido', NULL, NULL),
    (7, 1, 'Fibra 50 Mbps', 'Internet', 'Av. Belgrano 450, CABA', 5999.00, 500.00, 8.00, '2024-06-12', 25, 'Activo', '2025-06-12', '2025-06-12')
ON CONFLICT (id) DO UPDATE SET
    discount_percentage = EXCLUDED.discount_percentage,
    promo_end_date = EXCLUDED.promo_end_date;

-- Asignar customer_id = 1 a filas que lo tengan NULL
UPDATE services SET customer_id = 1 WHERE customer_id IS NULL;

-- Ajustar secuencia por si se insertó con id fijo
SELECT setval('services_id_seq', (SELECT COALESCE(MAX(id), 1) FROM services));
