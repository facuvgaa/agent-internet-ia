from __future__ import annotations

import logging

from context_llm.contexts import ROUTING_PROMPT, get_system_prompt
from graph.state import AgentState
from llm.bedrock_client import BedrockClient
from mcp_client.client import call_tool_sync, get_tools_by_domain

logger = logging.getLogger(__name__)

# Singleton; boto3 client es thread-safe
_bedrock = BedrockClient()

# Pasos que indican una conversación cerrada / dominio a reiniciar
_PASOS_CERRADOS = {
    "cerrado",
    "no_elegible",
    "sin_ofertas",
    "retencion_aplicada",
    "promesa_procesada",
    "reclamo_procesado",
}


# ── Nodo de routing ──────────────────────────────────────────────────────────

def routing_node(state: AgentState) -> dict:
    """
    Detecta el dominio del mensaje y actualiza state["domain"].
    Si ya hay un dominio activo y la conversación no está cerrada, lo mantiene.
    Si la conversación cerró o es el primer turno, clasifica con el LLM Haiku.
    """
    current_domain = state.get("domain", "")
    paso = state.get("paso_actual", "")

    if current_domain and paso not in _PASOS_CERRADOS:
        # Conversación activa: mantener dominio salvo cambio explícito a billing
        if current_domain == "retention":
            ultimo_texto = _last_human_text(state["messages"])
            if _is_billing(ultimo_texto):
                logger.info(
                    "[routing] Cambio retention→billing para customer %s",
                    state["customer_id"],
                )
                return {"domain": "billing", "paso_actual": "en_progreso"}
        logger.info(
            "[routing] Manteniendo dominio '%s' para customer %s",
            current_domain,
            state["customer_id"],
        )
        return {}  # Sin cambios en el estado

    # Primera vez o dominio cerrado: clasificar con LLM
    ultimo_texto = _last_human_text(state["messages"])
    nuevo_domain = _classify_domain(ultimo_texto)
    logger.info(
        "[routing] Dominio detectado: '%s' para customer %s",
        nuevo_domain,
        state["customer_id"],
    )
    return {"domain": nuevo_domain, "paso_actual": "en_progreso"}


def _last_human_text(messages: list[dict]) -> str:
    """Extrae el texto del último mensaje con role 'user'."""
    for m in reversed(messages):
        if m.get("role") == "user":
            for block in m.get("content", []):
                if "text" in block:
                    return block["text"]
    return ""


def _is_billing(text: str) -> bool:
    """Detecta si el texto refiere a facturación/servicios."""
    keywords = ["factura", "pago", "cobr", "servicio", "internet", "plan", "cargo", "deuda"]
    t = text.lower()
    return any(kw in t for kw in keywords)


def _classify_domain(text: str) -> str:
    """Llama al LLM Haiku para clasificar el dominio del mensaje."""
    prompt = ROUTING_PROMPT.format(input_text=text)
    result = _bedrock.invoke_simple(prompt).strip().lower()
    if "retention" in result:
        return "retention"
    if "promise" in result:
        return "promise"
    if "billing" in result:
        return "billing"
    return "general"


# ── Nodos agente ─────────────────────────────────────────────────────────────

def billing_agent_node(state: AgentState) -> dict:
    return _run_agent(state, "billing")


def retention_agent_node(state: AgentState) -> dict:
    return _run_agent(state, "retention")


def promise_agent_node(state: AgentState) -> dict:
    return _run_agent(state, "promise")


def general_agent_node(state: AgentState) -> dict:
    return _run_agent(state, "general")


def _run_agent(state: AgentState, domain: str) -> dict:
    """
    Función genérica para ejecutar un nodo agente:
    1. Obtiene tools MCP filtrados por dominio.
    2. Construye el system prompt con el customer_id.
    3. Llama a BedrockClient.run_agent_loop() con el historial de mensajes.
    4. Retorna los mensajes nuevos y la respuesta final.
    """
    customer_id = state["customer_id"]
    tools = get_tools_by_domain(domain)
    system = get_system_prompt(domain, customer_id)

    existing_count = len(state["messages"])
    updated_messages, final_text = _bedrock.run_agent_loop(
        messages=state["messages"],
        system_prompt=system,
        tools=tools,
        tool_executor=call_tool_sync,
    )

    # Solo los mensajes nuevos para el reducer _merge_messages
    new_messages = updated_messages[existing_count:]
    paso = _detect_paso(final_text, domain)

    logger.info(
        "[%s_agent] customer=%s paso=%s respuesta=%d chars",
        domain,
        customer_id,
        paso,
        len(final_text),
    )

    return {
        "messages": new_messages,
        "ultima_respuesta": final_text,
        "paso_actual": paso,
    }


def _detect_paso(text: str, domain: str = "") -> str:
    """
    Detecta si el flujo concluyó o se cerró basándose en el texto de respuesta.
    Retorna uno de los valores de _PASOS_CERRADOS o 'en_progreso'.
    """
    t = text.lower()

    # Marcadores de cierre por dominio
    if domain == "retention":
        if any(kw in t for kw in ["retención registrada", "quedaron registradas", "promociones quedaron"]):
            return "retencion_aplicada"
    if domain == "promise":
        if "promesa de pago quedó registrada" in t:
            return "promesa_procesada"
    if domain == "billing":
        if "reclamo registrado" in t:
            return "reclamo_procesado"

    # Marcadores de respuesta final satisfactoria (conversación sigue abierta)
    cierre = [
        "¿necesitás algo más?",
        "¿hay algo más",
        "con gusto",
        "quedó registrad",
        "estamos a tu disposición",
        "que tengas un buen día",
    ]
    if any(kw in t for kw in cierre):
        return "respondido"

    return "en_progreso"
