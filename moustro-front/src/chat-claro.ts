import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

// ─── Constantes ────────────────────────────────────────────────────────────
const CUSTOMER_ID = '1';
const WS_URL = 'http://localhost:8080/ws';

// ─── Refs al DOM (capturadas una sola vez al cargar el módulo) ──────────────
const msgList     = document.getElementById('chat-message-list') as HTMLElement;
const chatInput   = document.getElementById('chat-input') as HTMLInputElement;
const sendBtn     = document.getElementById('send-btn') as HTMLButtonElement;
const statusEl    = document.getElementById('header-status') as HTMLElement;
const ballSvg     = document.getElementById('ball-svg') as unknown as SVGSVGElement;
const thinkingTxt = document.getElementById('thinking-txt') as HTMLElement;
const ballG       = document.getElementById('ball-g') as unknown as SVGGElement;
const pts         = Array.from(document.querySelectorAll<SVGCircleElement>('.pt'));
const fileInput   = document.getElementById('file-input') as HTMLInputElement;

if (!msgList || !chatInput || !sendBtn) {
  console.error('[chat] Elementos críticos no encontrados en el DOM:', {
    msgList,
    chatInput,
    sendBtn,
  });
}

// ─── Estado ────────────────────────────────────────────────────────────────
let busy = false;
let stompReady = false;
let pendingResolve: ((txt: string) => void) | null = null;
let pendingImage: string | null = null;

// ─── Helpers DOM ───────────────────────────────────────────────────────────
function addMsg(text: string | null, who: 'user' | 'bot', img?: string | null): HTMLElement {
  const d = document.createElement('div');
  d.className = `msg ${who}`;
  if (text) d.textContent = text;
  if (img) {
    const imgEl = document.createElement('img');
    imgEl.src = img;
    d.appendChild(imgEl);
  }
  msgList.appendChild(d);
  msgList.scrollTop = msgList.scrollHeight;
  return d;
}

function typeWriter(el: HTMLElement, text: string, speed = 15): Promise<void> {
  let i = 0;
  el.textContent = '';
  el.classList.add('typing-cursor');
  return new Promise<void>((res) => {
    const iv = setInterval(() => {
      el.textContent += text[i++];
      msgList.scrollTop = msgList.scrollHeight;
      if (i >= text.length) {
        clearInterval(iv);
        el.classList.remove('typing-cursor');
        res();
      }
    }, speed);
  });
}

function setSendBtnState() {
  const hasContent = chatInput.value.trim().length > 0 || pendingImage != null;
  sendBtn.disabled = busy;
  sendBtn.classList.toggle('send-btn--inactive', !busy && !hasContent);
}

// ─── Animación pelota ───────────────────────────────────────────────────────
const ANGLES = [0, 45, 90, 135, 180, 225, 270, 315].map((a) => (a * Math.PI) / 180);
const DIST = 22;
let floatRaf: number | null = null;
let floatT = 0;

function ease(t: number) {
  return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
}

function setPt(el: SVGCircleElement, x: number, y: number, op: number) {
  el.setAttribute('cx', x.toFixed(2));
  el.setAttribute('cy', y.toFixed(2));
  el.setAttribute('opacity', op.toFixed(3));
}

function animOut(done?: () => void) {
  let t = 0;
  const run = () => {
    t = Math.min(t + 0.055, 1);
    const e = ease(t);
    ballG.setAttribute('opacity', (1 - e).toFixed(3));
    ballG.setAttribute('transform', `scale(${(1 - e * 0.85).toFixed(3)})`);
    pts.forEach((p, i) => {
      const d = e * DIST;
      setPt(p, Math.cos(ANGLES[i]) * d, Math.sin(ANGLES[i]) * d, e > 0.05 ? Math.min(e * 1.3, 1) : 0);
    });
    if (t < 1) requestAnimationFrame(run);
    else done?.();
  };
  requestAnimationFrame(run);
}

function startFloat() {
  floatT = 0;
  const base = ANGLES.map((a) => ({ bx: Math.cos(a) * DIST, by: Math.sin(a) * DIST }));
  const run = () => {
    floatT += 0.035;
    pts.forEach((p, i) => {
      const ox = Math.sin(floatT * 1.1 + i * 0.8) * 3;
      const oy = Math.cos(floatT * 0.9 + i * 0.6) * 3;
      const op = 0.45 + 0.55 * Math.sin(floatT * 1.8 + i);
      setPt(p, base[i].bx + ox, base[i].by + oy, op);
    });
    floatRaf = requestAnimationFrame(run);
  };
  floatRaf = requestAnimationFrame(run);
}

function animIn(done?: () => void) {
  if (floatRaf != null) cancelAnimationFrame(floatRaf);
  const snap = pts.map((p) => ({
    x: parseFloat(p.getAttribute('cx') || '0'),
    y: parseFloat(p.getAttribute('cy') || '0'),
  }));
  let t = 0;
  ballG.setAttribute('opacity', '0');
  ballG.setAttribute('transform', 'scale(0)');
  const run = () => {
    t = Math.min(t + 0.048, 1);
    const e = ease(t);
    const sc = e < 0.8 ? e / 0.8 : 1 + 0.07 * Math.sin(((e - 0.8) / 0.2) * Math.PI);
    ballG.setAttribute('transform', `scale(${sc.toFixed(3)})`);
    ballG.setAttribute('opacity', e.toFixed(3));
    pts.forEach((p, i) => {
      setPt(p, snap[i].x * (1 - e), snap[i].y * (1 - e), (1 - e) > 0.02 ? 1 - e : 0);
    });
    if (t < 1) requestAnimationFrame(run);
    else {
      pts.forEach((p) => setPt(p, 0, 0, 0));
      done?.();
    }
  };
  requestAnimationFrame(run);
}

function showBall() {
  ballSvg.style.display = 'block';
  thinkingTxt.style.display = 'block';
  statusEl.textContent = 'Pensando...';
}

function hideBall() {
  ballSvg.style.display = 'none';
  thinkingTxt.style.display = 'none';
  statusEl.textContent = stompReady ? 'En línea' : 'Desconectado';
}

// ─── Envío ──────────────────────────────────────────────────────────────────
async function doSend() {
  if (busy) return;

  const text = chatInput.value.trim();
  const img = pendingImage;

  if (!text && !img) return;

  // Mostrar el mensaje del usuario en el chat inmediatamente
  addMsg(text || null, 'user', img || null);
  chatInput.value = '';
  chatInput.placeholder = 'Escribí tu consulta...';
  pendingImage = null;
  setSendBtnState();

  // Sin conexión al backend
  if (!stompReady) {
    addMsg('Sin conexión con el servidor (Spring Boot :8080). Levantalo y recargá la página.', 'bot');
    return;
  }

  busy = true;
  setSendBtnState();

  showBall();
  await new Promise<void>((r) => animOut(r));
  startFloat();

  let responseText: string;
  try {
    responseText = await new Promise<string>((resolve, reject) => {
      const timer = setTimeout(() => {
        pendingResolve = null;
        reject(new Error('Sin respuesta del servidor (60 s)'));
      }, 60_000);
      pendingResolve = (txt) => {
        clearTimeout(timer);
        pendingResolve = null;
        resolve(txt);
      };
      stompClient.publish({
        destination: '/app/chat',
        body: JSON.stringify({ contenido: text || '(imagen)', customerId: CUSTOMER_ID }),
      });
    });
  } catch (err) {
    if (floatRaf != null) cancelAnimationFrame(floatRaf);
    await new Promise<void>((r) => animIn(r));
    hideBall();
    addMsg(err instanceof Error ? err.message : 'Error de conexión', 'bot');
    busy = false;
    setSendBtnState();
    chatInput.focus();
    return;
  }

  await new Promise<void>((r) => animIn(r));
  hideBall();
  await new Promise((r) => setTimeout(r, 120));

  const botDiv = addMsg('', 'bot');
  await typeWriter(botDiv, responseText);

  busy = false;
  setSendBtnState();
  chatInput.focus();
}

// ─── Adjuntar imagen ────────────────────────────────────────────────────────
fileInput.addEventListener('change', () => {
  const file = fileInput.files?.[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = (ev) => {
    pendingImage = ev.target?.result as string;
    chatInput.placeholder = 'Imagen adjunta...';
    setSendBtnState();
  };
  reader.readAsDataURL(file);
});

// ─── Eventos de envío ───────────────────────────────────────────────────────
sendBtn.addEventListener('click', () => void doSend());

chatInput.addEventListener('keydown', (e) => {
  if (e.key !== 'Enter') return;
  e.preventDefault();
  void doSend();
});

chatInput.addEventListener('input', setSendBtnState);

// ─── STOMP ──────────────────────────────────────────────────────────────────
const stompClient = new Client({
  webSocketFactory: () => new SockJS(WS_URL) as unknown as WebSocket,
  reconnectDelay: 5000,
  onConnect: () => {
    stompReady = true;
    statusEl.textContent = 'En línea';
    setSendBtnState();
    stompClient.subscribe(`/user/${CUSTOMER_ID}/queue/chat`, (msg) => {
      try {
        const data = JSON.parse(msg.body) as { respuesta?: string };
        pendingResolve?.(data.respuesta ?? String(msg.body));
      } catch {
        pendingResolve?.(String(msg.body));
      }
    });
  },
  onDisconnect: () => {
    stompReady = false;
    statusEl.textContent = 'Desconectado';
    setSendBtnState();
  },
  onWebSocketError: () => {
    statusEl.textContent = 'Error de conexión';
  },
});

stompClient.activate();

// Estado inicial del botón
setSendBtnState();
