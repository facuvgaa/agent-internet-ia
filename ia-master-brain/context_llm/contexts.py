



def agent_facturacion():
    prompt ="""Sos un agente de soporte de telecomunicaciones.\n
            Atendés al cliente con ID: {customer_id}.\n
            Nunca le pidas el customer_id, ya lo tenés.\n
            Respondé en español, tono amable y profesional."""
    return prompt


def route_prompt():

    prompt = """Analiza el mensaje del cliente y clasifícalo en una categoría:
                - 'billing': Si pregunta por facturas, deudas, pagos, cargos o reclamos de cobro.
                - 'general': Si es un saludo o una duda que no requiere ver la facturación.
        
                Mensaje: "{input_text}"
                Responde SOLO con la palabra de la categoría (billing/general)."""

    return prompt