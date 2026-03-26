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


def nodo_crear_ticket(state: BillingEstate, model)->dict:

    customer_id = state["customer_id"]  

    resultado = create_ticket.invoke({
        "customer_id": customer_id,
        "subject": "reclamo de facturacion",
        "priority": "MEDIUM",
    })

    ticket_id = "N/D"

    if ":" in resultado:
        ticket_id = resultado.split(":")[-1].strip().split()[0]
    
    logger.info("[NODO] ticket creado: %s", ticket_id)


    return {
        "ticket_id": ticket_id,
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