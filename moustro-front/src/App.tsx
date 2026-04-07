import { useState, useRef, useEffect, useCallback } from 'react';
import { useChat } from './hooks/useChat';
import './App.css';

interface MensajeUI {
  texto: string;
  esUsuario: boolean;
  imagen?: string;
  escribiendo?: boolean;
}

const ANGLES = [0, 45, 90, 135, 180, 225, 270, 315].map(a => a * Math.PI / 180);
const DIST = 22;

function ease(t: number) { return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t; }

export default function App() {
  const { mensajes: wsMsg, enviarMensaje, conectado, errorWs } = useChat('1');
  const [mensajesUI, setMensajesUI] = useState<MensajeUI[]>([
    { texto: 'Hola, ¿en qué puedo ayudarte hoy?', esUsuario: false }
  ]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState('En línea');
  const [ballVisible, setBallVisible] = useState(false);
  const [pendingImage, setPendingImage] = useState<string | null>(null);

  const messagesRef = useRef<HTMLDivElement>(null);
  const ballSvgRef  = useRef<SVGSVGElement>(null);
  const ballGRef    = useRef<SVGGElement>(null);
  const ptsRef      = useRef<SVGCircleElement[]>([]);
  const floatRafRef = useRef<number>(0);
  const floatTRef   = useRef(0);
  const prevWsLen   = useRef(0);

  const scrollBottom = () => {
    setTimeout(() => {
      if (messagesRef.current)
        messagesRef.current.scrollTop = messagesRef.current.scrollHeight;
    }, 10);
  };

  const setPt = (el: SVGCircleElement, x: number, y: number, op: number) => {
    el.setAttribute('cx', x.toFixed(2));
    el.setAttribute('cy', y.toFixed(2));
    el.setAttribute('opacity', op.toFixed(3));
  };

  const animOut = useCallback(() => new Promise<void>(done => {
    let t = 0;
    const run = () => {
      t = Math.min(t + 0.055, 1); const e = ease(t);
      if (ballGRef.current) {
        ballGRef.current.setAttribute('opacity', (1 - e).toFixed(3));
        ballGRef.current.setAttribute('transform', `scale(${(1 - e * 0.85).toFixed(3)})`);
      }
      ptsRef.current.forEach((p, i) => {
        if (!p) return;
        const d = e * DIST;
        setPt(p, Math.cos(ANGLES[i]) * d, Math.sin(ANGLES[i]) * d, e > 0.05 ? Math.min(e * 1.3, 1) : 0);
      });
      if (t < 1) requestAnimationFrame(run); else done();
    };
    requestAnimationFrame(run);
  }), []);

  const startFloat = useCallback(() => {
    floatTRef.current = 0;
    const base = ANGLES.map(a => ({ bx: Math.cos(a) * DIST, by: Math.sin(a) * DIST }));
    const run = () => {
      floatTRef.current += 0.035;
      ptsRef.current.forEach((p, i) => {
        if (!p || !base[i]) return;
        const ox = Math.sin(floatTRef.current * 1.1 + i * 0.8) * 3;
        const oy = Math.cos(floatTRef.current * 0.9 + i * 0.6) * 3;
        const op = 0.45 + 0.55 * Math.sin(floatTRef.current * 1.8 + i);
        setPt(p, base[i].bx + ox, base[i].by + oy, op);
      });
      floatRafRef.current = requestAnimationFrame(run);
    };
    floatRafRef.current = requestAnimationFrame(run);
  }, []);

  const animIn = useCallback(() => new Promise<void>(done => {
    cancelAnimationFrame(floatRafRef.current);
    const snap = ptsRef.current
      .filter((p): p is SVGCircleElement => p != null)
      .map(p => ({
        x: parseFloat(p.getAttribute('cx') || '0'),
        y: parseFloat(p.getAttribute('cy') || '0'),
      }));
    let t = 0;
    if (ballGRef.current) {
      ballGRef.current.setAttribute('opacity', '0');
      ballGRef.current.setAttribute('transform', 'scale(0)');
    }
    const run = () => {
      t = Math.min(t + 0.048, 1); const e = ease(t);
      const sc = e < 0.8 ? e / 0.8 : 1 + 0.07 * Math.sin((e - 0.8) / 0.2 * Math.PI);
      if (ballGRef.current) {
        ballGRef.current.setAttribute('transform', `scale(${sc.toFixed(3)})`);
        ballGRef.current.setAttribute('opacity', e.toFixed(3));
      }
      ptsRef.current.forEach((p, i) => {
        if (!p || !snap[i]) return;
        setPt(p, snap[i].x * (1 - e), snap[i].y * (1 - e), (1 - e) > 0.02 ? (1 - e) : 0);
      });
      if (t < 1) requestAnimationFrame(run);
      else {
        ptsRef.current.forEach(p => { if (p) setPt(p, 0, 0, 0); });
        done();
      }
    };
    requestAnimationFrame(run);
  }), []);

  const typeWriter = (texto: string, idx: number) => {
    let i = 0;
    const iv = setInterval(() => {
      i++;
      setMensajesUI(prev => prev.map((m, mi) =>
        mi === idx ? { ...m, texto: texto.slice(0, i), escribiendo: i < texto.length } : m
      ));
      scrollBottom();
      if (i >= texto.length) clearInterval(iv);
    }, 15);
  };

  // cuando llega respuesta del WebSocket
  useEffect(() => {
    if (wsMsg.length <= prevWsLen.current) return;
    prevWsLen.current = wsMsg.length;
    const ultimo = wsMsg[wsMsg.length - 1];
    if (ultimo.esUsuario) return;

    const textoBot = ultimo.texto;

    (async () => {
      await animIn();
      setBallVisible(false);
      setStatus('En línea');
      await new Promise(r => setTimeout(r, 120));

      setMensajesUI(prev => {
        const idx = prev.length;
        setTimeout(() => typeWriter(textoBot, idx), 0);
        return [...prev, { texto: '', esUsuario: false, escribiendo: true }];
      });
      scrollBottom();
      setBusy(false);
    })();
  }, [wsMsg, animIn]);

  const handleEnviar = async () => {
    if (busy || (!input.trim() && !pendingImage)) return;
    const texto = input.trim();
    const img   = pendingImage;

    setMensajesUI(prev => [...prev, { texto, esUsuario: true, imagen: img || undefined }]);
    setInput('');
    setPendingImage(null);
    setBusy(true);
    setStatus('Pensando...');
    setBallVisible(true);
    scrollBottom();

    await animOut();
    startFloat();
    enviarMensaje(texto);
  };

  const handleImagen = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (!f) return;
    const r = new FileReader();
    r.onload = ev => setPendingImage(ev.target?.result as string);
    r.readAsDataURL(f);
  };

  return (
    <div id="wrap">
      <div id="header">
        <div id="header-dot" className={conectado ? '' : 'offline'} />
        <div id="header-name">Asistente</div>
        <div id="header-status">{status}</div>
      </div>
      {errorWs && (
        <div
          style={{
            padding: '8px 16px',
            fontSize: 12,
            background: '#fef2f2',
            color: '#991b1b',
            borderBottom: '1px solid #fecaca',
          }}
        >
          {errorWs}
        </div>
      )}

      <div id="react-messages" ref={messagesRef}>
        {mensajesUI.map((m, i) => (
          <div key={i} className={`msg ${m.esUsuario ? 'user' : 'bot'}${m.escribiendo ? ' typing-cursor' : ''}`}>
            {m.texto}
            {m.imagen && <img src={m.imagen} alt="" style={{ maxWidth: '100%', borderRadius: 8, marginTop: 5, display: 'block' }} />}
          </div>
        ))}
      </div>

      <div id="ball-area">
        {ballVisible && (
          <>
            <svg id="ball-svg" ref={ballSvgRef} viewBox="-22 -22 44 44" xmlns="http://www.w3.org/2000/svg">
              <g id="ball-g" ref={ballGRef}>
                <circle cx="0" cy="0" r="14" fill="#b03030" opacity="0.9" />
                <circle cx="0" cy="0" r="14" fill="white" opacity="0.07" />
              </g>
              {[
                [2.8, '#b03030'], [2.2, '#882222'], [2.8, '#b03030'], [2, '#c04444'],
                [2.5, '#882222'], [2, '#b03030'], [2.8, '#c04444'], [2, '#882222']
              ].map(([r, fill], i) => (
                <circle
                  key={i}
                  ref={el => {
                    if (el) ptsRef.current[i] = el;
                    else delete ptsRef.current[i];
                  }}
                  cx="0"
                  cy="0"
                  r={r as number}
                  fill={fill as string}
                  opacity="0"
                />
              ))}
            </svg>
            <span id="thinking-txt">Pensando...</span>
          </>
        )}
      </div>

      <div id="input-area">
        <label className="icon-btn">
          <input id="file-input" type="file" accept="image/*" onChange={handleImagen} />
          <svg viewBox="0 0 24 24">
            <rect x="3" y="3" width="18" height="18" rx="3" />
            <circle cx="8.5" cy="8.5" r="1.5" />
            <polyline points="21 15 16 10 5 21" />
          </svg>
        </label>
        <input
          id="input-field"
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleEnviar()}
          placeholder={pendingImage ? 'Imagen adjunta...' : 'Escribí tu consulta...'}
          disabled={busy}
          autoComplete="off"
        />
        <button id="send-btn" onClick={handleEnviar} disabled={busy || (!input.trim() && !pendingImage) || !conectado}>
          <svg viewBox="0 0 24 24"><polygon points="22 2 15 22 11 13 2 9 22 2" /></svg>
        </button>
      </div>
    </div>
  );
}
