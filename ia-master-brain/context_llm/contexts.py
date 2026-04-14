



def agent_facturacion():
    prompt ="""Sos un agente de soporte de telecomunicaciones.\n
            Atendés al cliente con ID: {customer_id}.\n
            Nunca le pidas el customer_id, ya lo tenés.\n
            Respondé en español, tono amable y profesional."""
    return prompt


def route_prompt():

    prompt = """Analiza el mensaje del cliente y clasifícalo en una categoría:

                - 'retention': Si el cliente pide un descuento, una promoción, quiere bajar el precio de su factura, o amenaza con darse de baja.
                - 'billing': Si el mensaje trata sobre facturas, deudas, pagos, cargos, aumentos de precio, reclamos, o sobre los servicios/planes que el cliente tiene contratados (internet, TV, telefonía, pack, fibra, etc.).
                - 'general': Si es un saludo genérico o una consulta que no tiene relación con la cuenta, servicios o facturación del cliente.

                Mensaje: "{input_text}"
                Responde SOLO con la palabra de la categoría (retention/billing/general)."""

    return prompt