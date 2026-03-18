def save_conver():
    query = """
        INSERT INTO conversation_history (customer_id, orden, rol, contenido)
        VALUES (%s, %s, %s, %s)
    """
    return query

def recover_conver():

    query = """
        SELECT customer_id, orden, rol, contenido
        FROM conversation_history
        WHERE customer_id = %s
        ORDER BY created_at DESC, orden DESC
        LIMIT %s
    """
    return query