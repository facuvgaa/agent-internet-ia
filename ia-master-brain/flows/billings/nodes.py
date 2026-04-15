import json
import re
import logging
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from flows.billings.state import BillingEstate
from .utils import _limpiar_servicios
from tools import get_customer_info, billing_info, create_ticket, payment_promises, get_customer_service
from .prompts import EXPLICACION_FACTURA, SYSTEM_RECLAMO, EXPLICACION_SERVICIOS


logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def nodo_marcar_promise(state: BillingEstate) -> dict:
    return {"paso_actual": "ir_a_promise"}

def nodo_marcar_retention(state: BillingEstate) -> dict:
    return {"paso_actual": "ir_a_retention"}

def nodo_cargar_datos(state: BillingEstate)->dict:

    customer_id = state["customer_id"]
    info_cliente = get_customer_info.invoke({"customer_id": customer_id})
    cliente = info_cliente.get("data",{})
    resp_facturas = billing_info.invoke({"customer_id":customer_id})
    facturas = resp_facturas.get("data",{})


    return {
        "cliente": cliente,
        "facturas": facturas,
    }


def nodo_conversar(state: BillingEstate, model) -> dict:
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
        cliente_id=state["cliente"],
        servicios_info=servicios_ready
    )

    msgs = list(state["messages"])
    last_human = max((i for i, m in enumerate(msgs) if isinstance(m, HumanMessage)), default=None)
    if last_human is not None:
        msgs = msgs[:last_human + 1]
    mensajes_finales = [SystemMessage(content=prompt_ventas)] + msgs
    
    respuesta = model.invoke(mensajes_finales)

    return {
        "messages":    [respuesta],
        "servicios":   servicios_ready,
        "paso_actual": "info_servicios",
    }

def nodo_gestionar_reclamo(state: BillingEstate, model_haiku) -> dict:
    historial = state["messages"]
    conversacion_limpia = "\n".join([f"{type(m).__name__}: {m.content}" for m in historial])

    prompt_haiku = SYSTEM_RECLAMO.format(conversacion=conversacion_limpia)
    datos_ticket = model_haiku.invoke([SystemMessage(content=prompt_haiku)])
    contenido = datos_ticket.content.strip()

    match = re.search(r'\{[^{}]*"factura_id"[^{}]*\}', contenido, re.DOTALL)
    if match:
        contenido = match.group(0)
    elif "```json" in contenido:
        contenido = contenido.split("```json")[1].split("```")[0].strip()
    elif "```" in contenido:
        contenido = contenido.split("```")[1].split("```")[0].strip()

    try:
        datos_json = json.loads(contenido)
    except Exception:
        logger.error(f"Haiku fallo en el JSON. Contenido: {contenido}")
        datos_json = {}

    medio_pago    = datos_json.get("medio_pago")
    fecha_pago    = datos_json.get("fecha_pago")
    comprobante   = datos_json.get("comprobante")
    factura_id    = datos_json.get("factura_id")
    tipo_reclamo  = datos_json.get("tipo_reclamo") or "Reclamo de facturación"
    descripcion   = datos_json.get("descripcion") or tipo_reclamo
    prioridad     = datos_json.get("prioridad") or "alta"

    # Si faltan datos reales, pedir al cliente lo que falta
    faltantes = []
    if not medio_pago:
        faltantes.append("medio de pago (ej: Mercado Pago, transferencia, etc.)")
    if not fecha_pago:
        faltantes.append("fecha en que realizaste el pago")
    if not comprobante:
        faltantes.append("número de comprobante o transacción")

    if faltantes:
        lista = "\n".join(f"- {f}" for f in faltantes)
        msg = f"Para registrar el reclamo necesito que me confirmes:\n{lista}"
        logger.info("[reclamo] datos incompletos, faltan: %s", faltantes)
        return {
            "messages":    [AIMessage(content=msg)],
            "paso_actual": "esperando_datos_reclamo",
        }

    # Tenemos todo — crear ticket
    base = f"[{factura_id}] " if factura_id else ""
    subject = f"{base}{tipo_reclamo} — {descripcion} | Comprobante: {comprobante}"
    subject = subject[:200]

    resultado_api = create_ticket.invoke({
        "customer_id": int(state["customer_id"]),
        "subject":     subject,
        "priority":    prioridad,
    })

    ticket_id = resultado_api.get("ticket_id", "ERROR") if isinstance(resultado_api, dict) else "ERROR"
    logger.info("[reclamo] ticket creado id=%s subject=%s", ticket_id, subject)

    factura_str = f"**{factura_id}**" if factura_id else "—"
    msg_confirmacion = (
        f"✅ Reclamo registrado correctamente. Acá el resumen:\n\n"
        f"| Dato | Detalle |\n"
        f"|---|---|\n"
        f"| 🎫 N° de reclamo | **#{ticket_id}** |\n"
        f"| 📋 Motivo | {tipo_reclamo} |\n"
        f"| 📄 Factura | {factura_str} |\n"
        f"| 📅 Fecha de pago | {fecha_pago} |\n"
        f"| 💳 Medio de pago | {medio_pago} |\n"
        f"| 🔢 Comprobante | {comprobante} |\n"
        f"| ⚡ Prioridad | {prioridad.upper()} |\n\n"
        f"_{descripcion}_\n\n"
        f"El equipo de cobranzas va a verificar tu pago en las próximas 48 hs hábiles. "
        f"Mientras tanto **el servicio no se corta**. ¿Necesitás algo más?"
    )
    return {
        "messages":    [AIMessage(content=msg_confirmacion)],
        "ticket_id":   ticket_id,
        "paso_actual": "reclamo_procesado",
    }



