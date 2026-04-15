import json
import logging
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from flows.retention.state import RetentionState
from tools.tools import get_customer_info, get_customer_service ,get_retention_eligibility, get_retention_tiers, get_retention_preview, apply_retention_agreement
from .promps import PROMPT_NEGOCIACION


logger = logging.getLogger(__name__)


TIER_DISCOUNTS = {1: 25, 2: 50, 3: 65, 4: 80}

def nodo_cargar_datos(state: RetentionState) -> dict:
    customer_id = state["customer_id"]
    customer_info = get_customer_info.invoke({"customer_id": customer_id})
    cliente = customer_info.get("data", {})
    servicios_raw = get_customer_service.invoke({"customer_id": customer_id})
    servicios = servicios_raw.get("data", [])
    eligibility_raw = get_retention_eligibility.invoke({"customer_id": customer_id})
    eligibility = eligibility_raw.get("data", {})
    logger.info(
        "[retention] customer_id=%s eligible=%s servicios=%s",
        customer_id,
        eligibility.get("eligible"),
        len(servicios),
    )
    if not eligibility.get("eligible", False):
        return {
            "cliente": cliente,
            "eligibility": eligibility,
            "paso_actual": "no_elegible",
            "messages": [AIMessage(content=eligibility.get("message", "No tenés promociones disponibles en este momento."))],
        }
    return {
        "cliente": cliente,
        "servicios": servicios,
        "eligibility": eligibility,
        "paso_actual": "cargar_datos",
    }


def nodo_generar_oferta(state: RetentionState) -> dict:

    customer_id= state["customer_id"]

    servicios = state.get("servicios") or []
    
    ofertas = []

    for servicio in servicios:
        service_id = servicio.get("id")
        descuento_actual = float(servicio.get("discountPercentage") or 0)
        
        elig_raw = get_retention_eligibility.invoke({
            "customer_id":customer_id,
            "service_id": service_id,
        })
        elig = elig_raw.get("data",{})

        if not elig.get("eligible", False):
            logger.info("[retention] servicio %s no elegible, skip", service_id)
            continue

        allowed_level = elig.get("allowedLevels") or []

        niveles_validos = sorted([
            lvl for lvl in allowed_level
            if TIER_DISCOUNTS.get(lvl, 0) > descuento_actual
        ])

        if not niveles_validos:
            logger.info("[retention] servicio %s ya tiene %.0f%% >= niveles disponibles, skip", service_id, descuento_actual)
            continue

        nivel_inicial = niveles_validos[0]

        preview_raw = get_retention_preview.invoke({
            "customer_id": customer_id,
            "service_id": service_id,
            "level": nivel_inicial,
        })


        preview = preview_raw.get("data", {})


        ofertas.append({
            "service_id": service_id,
            "service_name": servicio.get("serviceName"),
            "descuento_actual": descuento_actual,
            "niveles_validos": niveles_validos,
            "nivel_actual": nivel_inicial,
            "discount_percent": preview.get("discountPercent"),
            "base_price": preview.get("baseMonthlyAmount"),
            "precio_con_descuento": preview.get("estimatedMonthlyAfterDiscount"),
            "duration_months": preview.get("durationMonths"),
            "cashback_app": preview.get("appPaymentCashbackPercent"),
        })

    logger.info("[retention] customer_id=%s ofertas generadas=%s", customer_id, len(ofertas))
    paso = "oferta_generada" if ofertas else "sin_ofertas"
    return {"ofertas_preview": ofertas, "paso_actual": paso}


def nodo_negociar(state: RetentionState, model) -> dict:
    ofertas = state.get("ofertas_preview") or []
    cashback = (state.get("eligibility") or {}).get("appPaymentCashbackPercent", 15)
    prompt = PROMPT_NEGOCIACION.format(ofertas=ofertas, cashback=cashback)
    msgs = state["messages"]
    # Tomamos solo hasta el último mensaje humano para no repetir contexto
    ultimo_humano = next((i for i in range(len(msgs) - 1, -1, -1) if isinstance(msgs[i], HumanMessage)), None)
    if ultimo_humano is not None:
        msgs = msgs[:ultimo_humano + 1]
    mensajes = [SystemMessage(content=prompt)] + msgs
    respuesta = model.invoke(mensajes)
    return {"messages": [respuesta], "paso_actual": "negociando"}

def nodo_aplicar_retencion(state: RetentionState) -> dict:
    """Aplica todas las ofertas que están en ofertas_preview. No usa LLM."""
    ofertas = state.get("ofertas_preview") or []

    confirmaciones = []
    for oferta in ofertas:
        service_id = oferta.get("service_id")
        level      = oferta.get("nivel_actual")
        if service_id is None or level is None:
            logger.warning("[retention] oferta sin service_id o nivel_actual, skip: %s", oferta)
            continue
        msg = apply_retention_agreement.invoke({
            "customer_id": state["customer_id"],
            "service_id":  service_id,
            "level":       level,
            "channel":     "IA",
        })
        logger.info("[retention] aplicado service_id=%s level=%s resultado=%s", service_id, level, msg)
        confirmaciones.append(msg)

    if confirmaciones:
        texto_final = (
            "✅ ¡Listo! Las promociones quedaron registradas en tu cuenta.\n\n"
            "En tu **próxima factura** vas a ver impactados los nuevos descuentos. "
            "Cualquier duda que tengas, estoy acá. ¿Necesitás algo más?"
        )
    else:
        texto_final = "No se pudo registrar ninguna promoción. Intentá de nuevo o contactá soporte."

    return {
        "messages":    [AIMessage(content=texto_final)],
        "paso_actual": "retencion_aplicada",
    }
