






def _limpiar_servicios(servicios_raw):
    procesados = []
    for s in servicios_raw:
        procesados.append({
            "nombre": s.get("serviceName"),
            "precio": s.get("basePrice"),
            "vencimiento_promo": s.get("promoExpiration"),
            "estado": s.get("status")
        })
    return procesados