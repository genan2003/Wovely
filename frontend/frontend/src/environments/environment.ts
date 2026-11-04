/**
 * Runtime endpoints (PLAN.md 4.1 / 5.4). Everything goes through the gateway on the same origin:
 * nginx in Docker, the Angular dev-server proxy (proxy.conf.json) in `ng serve`.
 */
export const environment = {
  /** REST base path, relative to the page origin. */
  apiUrl: '/api',
  /** STOMP endpoint path; the ws(s):// origin is derived from the page. */
  wsPath: '/ws',
};

/** `ws(s)://<host>/ws` for the current page. */
export function wsUrl(): string {
  const { protocol, host } = globalThis.location ?? { protocol: 'http:', host: 'localhost:4200' };
  return `${protocol === 'https:' ? 'wss' : 'ws'}://${host}${environment.wsPath}`;
}
