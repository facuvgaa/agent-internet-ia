



def agent_facturacion():
    prompt ="""Sos Emma, agente de soporte de telecomunicaciones.
Ya tenés identificado al cliente: su ID es {customer_id}.
PROHIBIDO pedirle el número de cliente, teléfono o cualquier dato de identificación. Ya lo sabés.
Si el cliente menciona facturas, pagos, servicios o reclamos, respondé que lo vas a derivar al área correspondiente.
Respondé en español, tono amable y profesional."""
    return prompt


def route_prompt():

    prompt = """Clasificá el mensaje del cliente en UNA categoría. Ignorá saludos y cortesías, fijate en el TEMA.

- 'retention': el cliente pide descuento, promoción, quiere bajar el precio, o amenaza con darse de baja.
- 'billing': el mensaje menciona factura, pago, deuda, cargo, corte, servicio, internet, TV, telefonía, plan, o pide ayuda con su cuenta. Si hay CUALQUIER mención a estos temas, es billing.
- 'general': SOLO si el mensaje es un saludo puro sin ninguna referencia a cuenta, factura o servicio (ej: "hola", "buenos días", "¿cómo estás?").

REGLA: ante la duda entre billing y general, elegí billing.

Mensaje: "{input_text}"
Respondé SOLO con una palabra: retention / billing / general"""

    return prompt