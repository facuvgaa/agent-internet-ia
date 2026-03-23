import logging
from langgraph.graph import StateGraph, END
from flows.billings.state import BillingEstate
from flows.billings.nodes import (
    nodo_cargar_datos,
    nodo_explicar_factura,
    nodo_detectar_intencion,
    nodo_crear_ticket,
    nodo_confirmar_ticket,
)

logger = logging.getLogger(__name__)


def build_factura_graph(model):
    