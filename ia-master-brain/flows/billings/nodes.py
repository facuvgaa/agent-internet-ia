import logging
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from flows.billings.state import FacturaState
from flows.billings import prompts
from tools import get_customer_info, billing_info, create_ticket, payment_promises

logger = logging.getLogger(__name__)


def nodo_cargar_datos(state: FacturaState, model) -> dict:
    """
    Llama a las APIs y carga los datos del cliente y sus facturas.
    El LLM no interviene acá, es pura lógica.
    """
    customer_id = state["customer_id"]

    cliente = get_customer_info.invoke({"customer_id": customer_id})
    facturas = billing_info.invoke({"customer_id": customer_id})

    logger.info("[NODO] cargar_datos cliente=%s facturas=%s",
                cliente.get("status_code"), facturas.get("status_code"))

    return {
        "cliente":  cliente.get("data", {}),
        "facturas": facturas.get("data", []),
        "paso_actual": "explicando"
    }


def nodo_explicar_factura(state: FacturaState, model) -> dict:
    """
    El LLM toma los datos cargados y le explica la factura al cliente.
    """
    cliente  = state["cliente"]
    facturas = state["facturas"]
    nombre   = cliente.get("name") or cliente.get("nombre") or "cliente"

    # formatear facturas para el prompt
    detalle = "\n".join([
        f"- Factura #{f.get('id')}: ${f.get('amount')} | "
        f"vence {f.get('dueDate')} | estado: {f.get('status')}"
        for f in facturas
    ]) or "Sin facturas encontradas."

    system = SystemMessage(content=prompts.EXPLICAR_FACTURA.format(
        nombre=nombre,
        customer_id=state["customer_id"],
        detalle_facturas=detalle
    ))

    response = model.invoke([system, *state["messages"]])
    return {
        "messages": [response],
        "paso_actual": "esperando"
    }


def nodo_detectar_intencion(state: FacturaState, model) -> dict:
    """
    Detecta qué quiere hacer el cliente con su factura.
    El LLM responde SOLO con una palabra: reclamar | pagar | consultar | otro
    """
    ultimo = state["messages"][-1].content

    respuesta = model.invoke([
        SystemMessage(content=prompts.DETECTAR_INTENCION.format(
            mensaje=ultimo
        ))
    ])

    intencion = respuesta.content.strip().lower()
    logger.info("[NODO] intencion detectada: %s", intencion)

    return {"paso_actual": intencion}


def nodo_crear_ticket(state: FacturaState, model) -> dict:
    """
    Crea el ticket en el backend Java y guarda el ID.
    El backend ya valida que el cliente puede reclamar.
    """
    customer_id = state["customer_id"]

    resultado = create_ticket.invoke({
        "customer_id": customer_id,
        "subject": "Reclamo por factura",
        "priority": "MEDIUM"
    })

    # extraer ticket_id del mensaje que devuelve el tool
    ticket_id = resultado.split(":")[-1].strip().split(".")[0] \
                if ":" in resultado else "N/D"

    logger.info("[NODO] ticket creado: %s", ticket_id)

    nombre = state["cliente"].get("name") or "cliente"
    system = SystemMessage(content=prompts.CONFIRMAR_TICKET.format(
        nombre=nombre,
        customer_id=customer_id,
        ticket_id=ticket_id
    ))

    response = model.invoke([system, *state["messages"]])
    return {
        "messages": [response],
        "ticket_id": ticket_id,
        "paso_actual": "cerrado"
    }


def nodo_promesa_pago(state: FacturaState, model) -> dict:
    """
    Registra la promesa de pago.
    Le pide la fecha al cliente si no la tiene todavía.
    """
    customer_id = state["customer_id"]
    facturas    = state["facturas"] or []
    ultimo_msg  = state["messages"][-1].content

    # buscar fecha en el mensaje del cliente (simple)
    import re
    fechas = re.findall(r'\d{4}-\d{2}-\d{2}|\d{2}/\d{2}/\d{4}', ultimo_msg)

    if not fechas:
        # no dio fecha todavía, pedirla
        response = AIMessage(
            content="¿Hasta qué fecha podés realizar el pago? "
                    "Indicame en formato DD/MM/AAAA."
        )
        return {"messages": [response]}

    # tomar la primera factura pendiente
    factura_pendiente = next(
        (f for f in facturas if f.get("status") in ("PENDING", "OVERDUE")),
        facturas[0] if facturas else {}
    )
    billing_id  = factura_pendiente.get("id")
    fecha_limite = fechas[0]

    resultado = payment_promises.invoke({
        "customer_id":   customer_id,
        "billing_id":    billing_id,
        "promise_until": fecha_limite
    })

    nombre = state["cliente"].get("name") or "cliente"
    system = SystemMessage(content=prompts.CONFIRMAR_PROMESA.format(
        nombre=nombre,
        customer_id=customer_id,
        factura_id=billing_id,
        fecha_limite=fecha_limite
    ))

    response = model.invoke([system, *state["messages"]])
    return {
        "messages":    [response],
        "promesa_pago": {"billing_id": billing_id, "fecha": fecha_limite},
        "paso_actual":  "cerrado"
    }