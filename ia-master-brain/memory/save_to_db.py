from dotenv import load_dotenv
import os
import psycopg  
load_dotenv()
def conexion_to_db():
    """
    Devuelve una conexión a la base de conversaciones (conversation-db).
    Usa la URL de CONVERSATION_DB del .env.
    """
    conn_str = os.getenv(
        "CONVERSATION_DB",
        "postgresql://admin-llm:admin-llm@localhost:5433/conversation-db",
    )
    conn = psycopg.connect(conn_str, autocommit=True)
    return conn
    