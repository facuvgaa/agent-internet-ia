import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
}

interface State {
  err: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { err: null };

  static getDerivedStateFromError(err: Error): State {
    return { err };
  }

  componentDidCatch(err: Error, info: ErrorInfo) {
    console.error('ErrorBoundary:', err, info);
  }

  render() {
    if (this.state.err) {
      return (
        <div style={{ padding: 24, fontFamily: 'system-ui', maxWidth: 560 }}>
          <h1 style={{ color: '#b03030' }}>Algo falló al cargar el chat</h1>
          <pre style={{ background: '#f3f4f6', padding: 12, overflow: 'auto' }}>
            {this.state.err.message}
          </pre>
          <p>Revisá la consola del navegador (F12) para más detalle.</p>
        </div>
      );
    }
    return this.props.children;
  }
}
