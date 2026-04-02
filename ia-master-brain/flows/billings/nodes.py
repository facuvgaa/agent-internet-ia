import logging
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from flows.billings.state import BillingEstate
from flows.billings import prompts
from utils import _limpiar_servicios
from tools import get_customer_info, billing_info, create_ticket, payment_promises,get_customer_service

from prompts import EXPLICACION_FACTURA, SYSTEM_RECLAMO, EXPLICACION_SERVICIOS




def nodo_cargar_datos(state: BillingEstate)->dict:

    customer_id = state["customer_id"]
    info_cliente = get_customer_info.invoke({"customer_id": customer_id})
    cliente = info_cliente.get("data",{})
    resp_facturas = billing_info.invoke({"customer_id":customer_id})
    facturas = resp_facturas.get("data",{})


    return {
        "cliente":cliente,
        "facturas":facturas,
        "pasa_Actual": "explicar factura"
    }


def nodo_conversar(state:BillingEstate, model)-> dict:
   
    prompt_formateado = EXPLICACION_FACTURA.format(

        cliente_nombre=state["cliente"],
        customer_id=state["customer_id"],
        contexto_facturas=state["facturas"]
    )
    mensajes = [SystemMessage(content=prompt_formateado)] + state["messages"]
    respuesta = model.invoke(mensajes)

    return {"messages": [respuesta]}

def nodo_info_servicios(state: BillingEstate, model):
    
    if state.get("servicios") and len(state["servicios"]) > 0:
        servicios_ready = state["servicios"]
    else:
        resp = get_customer_service.invoke({"customer_id": state["customer_id"]})
        data_pesada = resp.get("data", [])
        servicios_ready = _limpiar_servicios(data_pesada)

    prompt_ventas = EXPLICACION_SERVICIOS.format(
        cliente=state["cliente"],
        servicios_info=servicios_ready
    )

    mensajes = [SystemMessage(content=prompt_ventas)] + state["messages"]
    respuesta = model.invoke(mensajes)

    return {
        "messages": [respuesta],
        "servicios": servicios_ready
    }

def nodo_gestionar_reclamo(state:BillingEstate, model_haiku)->dict:
    historial = state["messages"][-5:]
    prompt_haiku = SYSTEM_RECLAMO.format(conversacion=historial)

    datos_ticket = model_haiku.invoke([SystemMessage(content=prompt_haiku)])

    resultado_api = create_ticket.invoke({
        "customer_id": state["customer_id"],
        "factura_id": datos_ticket.get("factura_id"),
        "motivo": datos_ticket.get("motivo"),
        "prioridad": datos_ticket.get("prioridad")
    })

    ticket_id = resultado_api.get("data", {}).get("id", "ERROR")

    msg_confirmacion = f"He registrado tu reclamo con el ID: {ticket_id}. ¿Deseas consultar algo más?"
    return {"messages": [AIMessage(content=msg_confirmacion)], "ticket_id": ticket_id}

