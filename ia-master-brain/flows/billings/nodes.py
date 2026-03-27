import logging
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from flows.billings.state import BillingEstate
from flows.billings import prompts
from tools import get_customer_info, billing_info, create_ticket, payment_promises

logger = logging.getLogger(__name__)


def _ensure_turn_ends_with_user(messages: list) -> list:
    """Bedrock Converse: antes de otra respuesta del modelo debe haber un mensaje de usuario."""
    m = list(messages)
    if m and isinstance(m[-1], AIMessage):
        m.append(
            HumanMessage(
                content="Continuá la conversación con el cliente según el contexto y tu rol."
            )
        )
    return m


def _normalize_facturas_list(facturas) -> list:
    """API puede devolver lista de dicts, lista de str, o un dict envolviendo la lista."""
    if facturas is None:
        return []
    if isinstance(facturas, dict):
        for key in ("items", "invoices", "data", "facturas", "billings"):
            inner = facturas.get(key)
            if isinstance(inner, list):
                return inner
        return [facturas]
    if isinstance(facturas, list):
        return facturas
    return [facturas]


def _linea_detalle_factura(item) -> str:
    """Una línea por factura; claves = BillingDTO del backend (camelCase)."""
    if not isinstance(item, dict):
        return f"- {item}"
    summary = item.get("serviceSummary")
    resumen_srv = ""
    if isinstance(summary, list) and summary:
        resumen_srv = f" · servicios: {', '.join(str(s) for s in summary)}"
    return (
        f"- ID {item.get('id')} · cliente {item.get('customerId')} · Nº {item.get('invoiceNumber')} · "
        f"total ${item.get('totalAmount')} · período {item.get('periodLabel')} · "
        f"emisión {item.get('issueDate')} · vence {item.get('dueDate')} · "
        f"estado {item.get('status')} · "
        f"cargos actuales {item.get('currentCharges')} · "
        f"saldo anterior {item.get('previousBalance')} · "
        f"descuentos {item.get('discounts')} · intereses {item.get('interests')}"
        f"{resumen_srv}"
    )


def nodo_cargar_datos(state: BillingEstate, model)-> dict:

    customer_id = state["customer_id"]

    resp_client = get_customer_info.invoke({"customer_id":customer_id})
    cliente = resp_client.get("data",{})

    resp_facturas = billing_info.invoke({"customer_id":customer_id})
    facturas = _normalize_facturas_list(resp_facturas.get("data"))

    logger.info("[NODO] cargar_datos customer=%s facturas=%s", customer_id, len(facturas))

    return {
        "cliente": cliente,
        "facturas": facturas,
        "paso_actual": "explicando"
    }



def nodo_explicar_factura(state:BillingEstate, model)->dict:

    cliente = state["cliente"]
    facturas = state["facturas"]
    nombre   = cliente.get("name") or cliente.get("nombre") or "cliente"

    rows = _normalize_facturas_list(facturas)
    if rows:
        detalle = "\n".join(_linea_detalle_factura(item) for item in rows)
    else:
        detalle = "No se encontraron facturas."

    system = SystemMessage(content=prompts.EXPLICAR_FACTURA.format(
        nombre=nombre,
        customer_id=state["customer_id"],
        detalle_facturas=detalle,
    ))

    response = model.invoke([system, *_ensure_turn_ends_with_user(state["messages"])])

    return {
        "messages": [response],
        "paso_actual": "esperando"
    }

def nodo_detectar_intencion(state: BillingEstate, model)-> dict:
    ultimo = state["messages"][-1].content

    respuesta = model.invoke([
        SystemMessage(content=prompts.DETECTAR_INTENCION.format(
            mensaje=ultimo
        ))
    ])

    intencion = (respuesta.content or "").strip().lower()
    logger.info("[NODO] intencion detectada: %s", intencion)

    return {"paso_actual": intencion}


def nodo_crear_ticket(state: BillingEstate, model) -> dict:
    customer_id = state["customer_id"]
    
    # subject descriptivo generado por el LLM, no hardcodeado
    motivo = state.get("motivo_reclamo") or "Reclamo por factura"
    
    # prioridad según el motivo
    # si menciona dificultad económica o cargo no reconocido → HIGH
    keywords_high = ["no reconoce", "no contrató", "no solicité", 
                     "no pedí", "dificultad", "no puedo pagar"]
    priority = "HIGH" if any(
        k in motivo.lower() for k in keywords_high
    ) else "MEDIUM"

    resultado = create_ticket.invoke({
        "customer_id": customer_id,
        "subject":     motivo,      # ← motivo real, no genérico
        "priority":    priority     # ← prioridad según contexto
    })

    ticket_id = "N/D"
    if ":" in resultado:
        ticket_id = resultado.split(":")[-1].strip().split(" ")[0]

    logger.info("[NODO] ticket creado id=%s subject='%s'", ticket_id, motivo)

    return {
        "ticket_id":   ticket_id,
        "paso_actual": "confirmar_ticket"
    }


def nodo_confirmar_ticket(state: BillingEstate, model)->dict:

    cliente = state["cliente"]
    nombre    = cliente.get("name") or cliente.get("nombre") or "cliente"
    ticket_id = state["ticket_id"]

    system = SystemMessage(
        content=prompts.CONFIRMAR_TICKET.format(
            nombre=nombre,
            customer_id=state["customer_id"],
            ticket_id=ticket_id,
        )
    )
    response = model.invoke(
        [system, *_ensure_turn_ends_with_user(state["messages"])]
    )

    return {
        "messages": [response],
        "paso_actual": "cerrado",
    }

def nodo_extraer_motivo(state: BillingEstate, model) -> dict:
    """
    Recolecta toda la información relevante de la conversación
    y arma un subject completo para el ticket.
    """
    conversacion = "\n".join([
        f"{'Cliente' if isinstance(m, HumanMessage) else 'Agente'}: {m.content}"
        for m in state["messages"]
    ])

    respuesta = model.invoke([
        SystemMessage(content="""Analizá esta conversación de soporte
y extraé toda la información relevante para armar un ticket de reclamo.

Respondé SOLO en JSON exacto, sin texto adicional:
{
  "numero_factura":   "FAC-2026-002 o null si no mencionó",
  "medio_pago":       "Mercado Pago / Pago Fácil / etc, o null",
  "comprobante":      "número de comprobante o null",
  "fecha_pago":       "fecha mencionada o null",
  "motivo":           "motivo principal en una oración",
  "detalle":          "todo el contexto relevante en 2-3 oraciones",
  "claro":            true si tiene al menos motivo y factura, false si no
}"""),
        HumanMessage(content=conversacion)
    ])

    import json as _json
    try:
        raw = respuesta.content.strip()
        # limpiar si el LLM envuelve en ```json
        if raw.startswith("```"):
            raw = raw.split("```")[1]
            if raw.startswith("json"):
                raw = raw[4:]
        data = _json.loads(raw.strip())
    except Exception:
        logger.warning("[NODO] no se pudo parsear JSON, usando fallback")
        data = {"claro": True, "motivo": "Reclamo solicitado por cliente"}

    # armar el subject completo para el ticket
    partes = []
    if data.get("motivo"):
        partes.append(data["motivo"])
    if data.get("numero_factura"):
        partes.append(f"Factura: {data['numero_factura']}")
    if data.get("medio_pago"):
        partes.append(f"Medio de pago: {data['medio_pago']}")
    if data.get("comprobante"):
        partes.append(f"Comprobante: {data['comprobante']}")
    if data.get("fecha_pago"):
        partes.append(f"Fecha de pago: {data['fecha_pago']}")
    if data.get("detalle"):
        partes.append(data["detalle"])

    subject = " | ".join(p for p in partes if p)

    # si el cliente insiste aunque no haya todo, crear igual
    mensajes_cliente = [
        m.content.lower() for m in state["messages"]
        if isinstance(m, HumanMessage)
    ]
    keywords_urgencia = [
        "inicia", "iniciá", "crear", "dale", "vamos",
        "cuantas veces", "ya te dije", "ahora", "hermano"
    ]
    cliente_insiste = any(
        k in msg
        for msg in mensajes_cliente
        for k in keywords_urgencia
    )

    claro = data.get("claro", False) or cliente_insiste

    logger.info("[NODO] subject='%s' claro=%s", subject, claro)

    return {
        "motivo_reclamo": subject,
        "motivo_claro":   claro
    }


def nodo_pedir_detalle(state: BillingEstate, model) -> dict:
    """
    Nodo PURPLE — el cliente fue cortante, el LLM le pide más contexto
    antes de crear el ticket.
    """
    nombre = state["cliente"].get("name") or "cliente"

    system = SystemMessage(content=f"""Sos un agente de soporte de telecomunicaciones.
Atendés a {nombre}.
El cliente quiere hacer un reclamo pero no explicó bien el motivo.
Pedile amablemente que te cuente con más detalle qué está mal:
¿es un cargo que no reconoce? ¿el monto es más alto que otros meses?
¿hay un servicio que no contrató? Sé breve y específico en la pregunta.""")

    response = model.invoke([system, *state["messages"]])

    return {
        "messages":    [response],
        "paso_actual": "esperando_detalle"
    }