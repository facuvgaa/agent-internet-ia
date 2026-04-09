


ESTADOS_IMPAGOS = ("OVERDUE", "DUE")
MAX_FACTURAS_PARA_PROMESA = 2


def _defeat_factura_filter(facturas: list) -> dict:
    
    impagadas = [f for f in facturas if f.get("status") in ESTADOS_IMPAGOS]

    puede_prometer = len(impagadas) <= MAX_FACTURAS_PARA_PROMESA

    return {
        "facturas": impagadas,
        "puede_prometer": puede_prometer,
        "total_impagadas": len(impagadas),
    }
