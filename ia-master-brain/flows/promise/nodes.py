import json 
import logging
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from flows.promise.state import PromiseEstate
from flows.promise.utils import _defeat_factura_filter
from tools.tools import get_customer_info, billing_info, payment_promises
from .promps import PROMPT_PROMISE_1, SYSTEM_PROMISE
logger = logging.getLogger(__name__)

def nodo_cargar_datos(state: PromiseEstate) -> dict:
    customer_id = state["customer_id"]

    info_cliente = get_customer_info.invoke({"customer_id": customer_id})
    cliente = info_cliente.get("data", {})

    facturas_raw = billing_info.invoke({"customer_id": customer_id})
    todas = facturas_raw.get("data", [])

    resultado = _defeat_factura_filter(todas)

    logger.info(
        "[promise] cliente=%s impagadas=%s puede_prometer=%s",
        customer_id,
        resultado["total_impagadas"],
        resultado["puede_prometer"],
    )

    return {
        "cliente": cliente,
        "factura_defeated": resultado["facturas"],
        "puede_prometer": resultado["puede_prometer"],
        "paso_actual": "cargar_datos",
    }


def nodo_explicacion_promesa(state: PromiseEstate, model) -> dict:
    prompt = PROMPT_PROMISE_1.format(
        factura_defeate=state["factura_defeated"],
        puede_prometer=state["puede_prometer"],
    )

    mensajes = [SystemMessage(content=prompt)] + state["messages"]
    respuesta = model.invoke(mensajes)

    return {
        "messages": [respuesta],
        "paso_actual": "explicacion_promesa",
    }



def nodo_ejecutar_promesa(state, model_haiku):
    historial = state["messages"]
    conversacion = "\n".join([f"{type(m).__name__}: {m.content}" for m in historial])

    from datetime import datetime, timedelta
    fecha_actual = datetime.now().isoformat()

    prompt = SYSTEM_PROMISE.format(
        conversacion=conversacion,
        fecha_actual=fecha_actual
    )
    resultado = model_haiku.invoke([SystemMessage(content=prompt)])
    datos = json.loads(resultado.content.strip())

    msg = payment_promises.invoke({
        "customer_id": int(state["customer_id"]),
        "billing_id": datos["billing_id"],
        "promise_until": datos["promise_until"]
    })

    return {"messages": [AIMessage(content=msg)], "paso_actual": "promesa_procesada"}
