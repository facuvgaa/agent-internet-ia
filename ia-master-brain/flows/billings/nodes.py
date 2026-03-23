import logging
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from flows.billings.state import BillingEstate, FacturaState
from flows.billings import prompts
from tools import get_customer_info, billing_info, create_ticket, payment_promises

logger = logging.getLogger(__name__)


def nodo_cargar_datos(state: BillingEstate, model)-> dict:

    customer_id = state["customer_id"]

    resp_client = get_customer_info.invoke({"customer_id":customer_id})
    cliente = resp_client.get("data",{})

    resp_facturas = billing_info.invoque({"customer_id":customer_id})
    facturas = resp_facturas.get("data",[])

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

    if facturas:
        detalle = "\n".join([
            f"- Factura #{f.get('id')}: "
            f"${f.get('amount')} | "
            f"vence {f.get('dueDate')} | "
            f"estado: {f.get('status')}"
            for f in facturas
        ])
    else:
        detalle = "No se encontraron facturas."


        system = SystemMessage(content=prompts.EXPLICAR_FACTURA.format(
            nombre = nombre,
            customer_id = state["customer_id"],
            detalle_factura = detalle
        ))

        response = model.invoque([system, *state["messages"]])

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

    intencion = respuesta.content.strip.lower()
    logger.info("[NODO] intencion detectada: %s", intencion)

    return {"paso_actual": intencion}


def nodo_crear_ticket(state: BillingEstate, model)->dict:

    customer_id = state["customer_id"]  

    resultado = create_ticket.invoke({
        "customer_id": customer_id,
        "subjet": "reclamo de facturacion",
        "priority": "MEDIUM"
    }) 

    ticket_id = "N/D"

    if ":" in resultado:
        ticket_id = resultado.split(":"[-1].strip().split(" ")[0])
    
    logger.info("[NODO] ticket creado: %s", ticket_id)


    return {
        "ticket_id": ticket_id,
        "paso_actual": "confirmar_ticket"
    }   


def nodo_confirmar_ticket(state: BillingEstate, model)->dict:

    cliente = state["cliente"]
    nombre    = cliente.get("name") or cliente.get("nombre") or "cliente"
    ticket_id = state["ticket_id"]


    system = SystemMessage(content=prompts.CONFIRMAR_TICKET.format(
        nombre = nombre,
        customer_id = state["customer_id"],
        ticket_id =  ticket_id
    )) 

    response = model.invoke([system, *state["messages"]])

    return {
        "messages": [response],
        "paso_actual": "cerrado"
    }