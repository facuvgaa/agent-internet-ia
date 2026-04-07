import { useState, useEffect, useRef, useCallback } from 'react';
import type { Client } from '@stomp/stompjs';

export interface Mensaje {
  texto: string;
  esUsuario: boolean;
}

export function useChat(customerId: string) {
  const [mensajes, setMensajes] = useState<Mensaje[]>([]);
  const [conectado, setConectado] = useState(false);
  const [errorWs, setErrorWs] = useState<string | null>(null);
  const clienteRef = useRef<Client | null>(null);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        const [{ Client }, sockMod] = await Promise.all([
          import('@stomp/stompjs'),
          import('sockjs-client'),
        ]);

        const SockJS = sockMod.default;
        if (typeof SockJS !== 'function') {
          throw new Error('sockjs-client: export default inválido');
        }

        if (cancelled) return;

        const stompClient = new Client({
          webSocketFactory: () => new (SockJS as unknown as new (url: string) => object)('http://localhost:8080/ws'),
          reconnectDelay: 5000,
          onConnect: () => {
            setConectado(true);
            setErrorWs(null);
            stompClient.subscribe(`/user/${customerId}/queue/chat`, (msg) => {
              try {
                const data = JSON.parse(msg.body) as { respuesta?: string };
                const texto = data.respuesta ?? String(msg.body);
                setMensajes((prev) => [...prev, { texto, esUsuario: false }]);
              } catch {
                setMensajes((prev) => [...prev, { texto: String(msg.body), esUsuario: false }]);
              }
            });
          },
          onDisconnect: () => setConectado(false),
          onStompError: (frame) => {
            setErrorWs(frame.headers['message'] ?? 'Error STOMP');
          },
          onWebSocketError: () => {
            setErrorWs('No se pudo conectar al servidor (¿Spring en :8080?)');
          },
        });

        clienteRef.current = stompClient;
        stompClient.activate();
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        setErrorWs(`WebSocket: ${msg}`);
        console.error(e);
      }
    })();

    return () => {
      cancelled = true;
      clienteRef.current?.deactivate();
      clienteRef.current = null;
    };
  }, [customerId]);

  const enviarMensaje = useCallback((texto: string) => {
    if (!clienteRef.current?.connected) return;
    setMensajes((prev) => [...prev, { texto, esUsuario: true }]);
    clienteRef.current.publish({
      destination: '/app/chat',
      body: JSON.stringify({ contenido: texto, customerId }),
    });
  }, [customerId]);

  return { mensajes, enviarMensaje, conectado, errorWs };
}
