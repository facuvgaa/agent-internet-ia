declare module 'sockjs-client' {
  class SockJS {
    constructor(url: string, _reserved?: unknown, options?: object);
    close(code?: number, reason?: string): void;
    send(data: string): void;
    readyState: number;
    onopen: (() => void) | null;
    onclose: ((e: { code: number; reason: string; wasClean: boolean }) => void) | null;
    onmessage: ((e: { data: string }) => void) | null;
  }
  export = SockJS;
}
