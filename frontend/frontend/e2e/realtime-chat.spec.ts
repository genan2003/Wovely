import { expect, test, WebSocketRoute } from '@playwright/test';
import { mockBackend } from './fixtures';

/** STOMP frames are `COMMAND\nheader:value\n\nbody\0`. */
function parseFrame(raw: string) {
  const [head, ...rest] = raw.replace(/\0$/, '').split('\n\n');
  const [command, ...headerLines] = head.split('\n');
  const headers = Object.fromEntries(headerLines.filter(Boolean).map((l) => [l.slice(0, l.indexOf(':')), l.slice(l.indexOf(':') + 1)]));
  return { command: command.trim(), headers, body: rest.join('\n\n') };
}

/** PLAN.md 3.4 — a pushed message updates the navbar badge without polling; the JWT rides in CONNECT. */
test('pushed chat message shows up in the unread badge', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('auth-user', JSON.stringify({ id: 'buyer-1', username: 'testuser', roles: ['ROLE_USER'] }));
    sessionStorage.setItem('auth-token', 'e2e-token');
  });
  await mockBackend(page, { '^/api/chat/unread/buyer-1$': [] });

  let connectAuth: string | undefined;
  let subscription: string | undefined;
  let socket: WebSocketRoute | undefined;
  await page.routeWebSocket(/\/ws$/, (ws) => {
    socket = ws;
    ws.onMessage((data) => {
      const frame = parseFrame(String(data));
      if (frame.command === 'CONNECT' || frame.command === 'STOMP') {
        connectAuth = frame.headers['Authorization'];
        ws.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
      } else if (frame.command === 'SUBSCRIBE') {
        subscription = frame.headers['id'];
        expect(frame.headers['destination']).toBe('/user/queue/messages');
      }
    });
  });

  await page.goto('/products');
  const messagesLink = page.getByRole('link', { name: /^Messages/ });
  await expect(messagesLink).toHaveAccessibleName('Messages');
  await expect.poll(() => subscription).toBeDefined();
  expect(connectAuth).toBe('Bearer e2e-token');
  expect(page.url()).not.toContain('e2e-token');

  const body = JSON.stringify({ id: 'm1', senderId: 'seller-1', receiverId: 'buyer-1', content: 'Your basket ships today' });
  socket!.send(`MESSAGE\ndestination:/user/queue/messages\nsubscription:${subscription}\nmessage-id:1\ncontent-type:application/json\n\n${body}\0`);

  await expect(messagesLink).toHaveAccessibleName('Messages, 1 unread');
  await expect(messagesLink).toContainText('1');
});
