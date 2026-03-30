import logging
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from flows.billings.state import BillingEstate
from flows.billings import prompts
from tools import get_customer_info, billing_info, create_ticket, payment_promises



def cargar_datos(state: BillingEstate, model):

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


def nodo_conversar(state:BillingEstate, model):
    cliente = state["cliente"]
    facturas = state["facturas"]
    