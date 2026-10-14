import asyncio
import logging
import threading
import time

from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client

logger = logging.getLogger(__name__)

_session: ClientSession | None = None
_tools_cache: list | None = None
_tools_bedrock_cache: list | None = None
_mcp_loop: asyncio.AbstractEventLoop | None = None

MCP_URL: str | None = None


async def _connect(url: str) -> None:
    global _session, _tools_cache, _tools_bedrock_cache
    async with streamable_http_client(url) as (read, write, _):
        async with ClientSession(read, write) as session:
            await session.initialize()
            _session = session
            result = await session.list_tools()
            _tools_cache = result.tools
            _tools_bedrock_cache = _convert_to_bedrock_format(_tools_cache)
            logger.info(
                "[MCP] Conectado. Tools disponibles: %d", len(_tools_cache)
            )
            # Mantener la conexión viva mientras el consumer corra
            await asyncio.Future()  # bloquea indefinidamente


def initialize_mcp(mcp_url: str) -> None:
    """
    Lanza la conexión MCP en un thread daemon con event loop separado.
    Bloquea hasta 10 segundos esperando que los tools estén disponibles.
    Si el servidor no está disponible, continúa degradado (sin tools).
    """
    global MCP_URL, _mcp_loop
    MCP_URL = mcp_url

    loop = asyncio.new_event_loop()
    _mcp_loop = loop

    t = threading.Thread(
        target=lambda: loop.run_until_complete(_connect(mcp_url)),
        daemon=True,
        name="mcp-event-loop",
    )
    t.start()

    # Esperar a que los tools estén disponibles (máx 10s)
    for _ in range(100):
        if _tools_bedrock_cache is not None:
            break
        time.sleep(0.1)

    tool_count = len(_tools_bedrock_cache or [])
    logger.info("[MCP] Inicializado con %d tools en %s", tool_count, mcp_url)


def get_tools_for_bedrock() -> list:
    """Retorna los tools en formato Bedrock toolConfig.tools."""
    return _tools_bedrock_cache or []


def get_tools_by_domain(domain: str) -> list:
    """
    Filtra los tools MCP según el dominio.
    Esto permite que cada nodo agente solo vea tools relevantes.
    """
    all_tools = get_tools_for_bedrock()

    BILLING_TOOLS = {
        "get_customer_info",
        "get_customer_service",
        "billing_info",
        "billing_lookup",
        "create_ticket",
        "run_network_diagnostic",
        "list_network_diagnostics",
        "get_latest_network_diagnostic",
        "request_connection_reset",
        "grant_mobile_topup",
        "list_available_offerings",
    }
    RETENTION_TOOLS = {
        "get_customer_info",
        "get_customer_service",
        "get_retention_tiers",
        "get_retention_eligibility",
        "get_retention_preview",
        "apply_retention_agreement",
    }
    PROMISE_TOOLS = {
        "get_customer_info",
        "billing_info",
        "payment_promises",
    }

    DOMAIN_FILTER: dict[str, set] = {
        "billing": BILLING_TOOLS,
        "retention": RETENTION_TOOLS,
        "promise": PROMISE_TOOLS,
        "general": {t["toolSpec"]["name"] for t in all_tools},
    }

    allowed = DOMAIN_FILTER.get(domain, set())
    return [t for t in all_tools if t["toolSpec"]["name"] in allowed]


def _convert_to_bedrock_format(mcp_tools: list) -> list:
    """
    Convierte objetos Tool del MCP SDK al formato toolConfig.tools de Bedrock.
    Maneja inputSchema tanto como dict como objeto Pydantic.
    """
    result = []
    for tool in mcp_tools:
        input_schema = tool.inputSchema
        # inputSchema puede ser un dict o un objeto Pydantic — normalizar a dict
        if hasattr(input_schema, "model_dump"):
            input_schema = input_schema.model_dump()
        result.append({
            "toolSpec": {
                "name": tool.name,
                "description": tool.description or "",
                "inputSchema": {"json": input_schema},
            }
        })
    return result


async def call_tool_async(tool_name: str, arguments: dict) -> str:
    """Ejecuta un tool MCP y retorna el resultado como string."""
    if _session is None:
        raise RuntimeError("MCP session no inicializada")
    result = await _session.call_tool(tool_name, arguments)
    parts = []
    for block in result.content:
        if hasattr(block, "text"):
            parts.append(block.text)
        else:
            parts.append(str(block))
    return "\n".join(parts)


def call_tool_sync(tool_name: str, arguments: dict) -> str:
    """
    Versión síncrona para llamar desde nodos LangGraph síncronos.
    Usa run_coroutine_threadsafe para ejecutar en el loop del thread MCP.
    """
    if _mcp_loop is None:
        raise RuntimeError("MCP loop no inicializado. Llamá initialize_mcp() primero.")
    future = asyncio.run_coroutine_threadsafe(
        call_tool_async(tool_name, arguments), _mcp_loop
    )
    return future.result(timeout=30)
