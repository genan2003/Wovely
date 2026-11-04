import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { vi } from 'vitest';
import type { IMessage, StompConfig } from '@stomp/stompjs';

import { RealtimeService } from './realtime.service';
import { StorageService } from './storage.service';
import { provideTestDefaults } from '../../testing/test-providers';
import { ChatMessage } from '../models/chat.model';

/** PLAN.md 3.4 — one STOMP connection per signed-in tab, JWT in CONNECT, own queue only. */
describe('RealtimeService', () => {
  let http: HttpTestingController;
  let service: RealtimeService;
  let token: string | null;
  let config: StompConfig;
  const fake = { activate: vi.fn(), deactivate: vi.fn(), subscribe: vi.fn(), connected: false };

  beforeEach(() => {
    token = 'jwt-1';
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        ...provideTestDefaults(),
        { provide: StorageService, useValue: { getToken: () => token, getUser: () => ({ id: 'me' }) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(RealtimeService);
    service.factory = (c) => {
      config = c as StompConfig;
      return fake as never;
    };
  });

  afterEach(() => http.verify());

  function startAndConnect(unread: ChatMessage[] = []) {
    service.start();
    http.expectOne((r) => r.url.endsWith('/api/chat/unread/me')).flush(unread);
    config.onConnect!({} as never);
  }

  const frame = (m: Partial<ChatMessage>) => ({ body: JSON.stringify(m) }) as IMessage;

  it('sends the current token in CONNECT and subscribes to the own queue', () => {
    startAndConnect([{ senderId: 'a', receiverId: 'me', content: 'x' }]);
    expect(fake.activate).toHaveBeenCalledTimes(1);
    expect(config.brokerURL).toMatch(/\/ws$/);

    token = 'jwt-2'; // rotated by the refresh interceptor
    const client = { connectHeaders: {} } as never as { connectHeaders: Record<string, string> };
    (config.beforeConnect as (c: unknown) => void)(client);
    expect(client.connectHeaders['Authorization']).toBe('Bearer jwt-2');

    expect(fake.subscribe).toHaveBeenCalledWith('/user/queue/messages', expect.any(Function));
    expect(service.connected()).toBe(true);
    expect(service.unreadCount()).toBe(1);
  });

  it('emits pushed messages and counts only those addressed to me', () => {
    startAndConnect();
    const seen: ChatMessage[] = [];
    service.messages$.subscribe((m) => seen.push(m));
    const onFrame = fake.subscribe.mock.calls[0][1] as (f: IMessage) => void;

    onFrame(frame({ id: '1', senderId: 'ana', receiverId: 'me', content: 'Hi' }));
    onFrame(frame({ id: '2', senderId: 'me', receiverId: 'ana', content: 'Hello' }));
    onFrame({ body: 'not json' } as IMessage);

    expect(seen.map((m) => m.id)).toEqual(['1', '2']);
    expect(service.unreadCount()).toBe(1);
  });

  it('does not connect without a token and connects only once', () => {
    token = null;
    service.start();
    expect(fake.activate).not.toHaveBeenCalled();
    token = 'jwt';
    startAndConnect();
    service.start();
    expect(fake.activate).toHaveBeenCalledTimes(1);
  });

  it('stop() disconnects and clears the badge', () => {
    startAndConnect([{ senderId: 'a', receiverId: 'me', content: 'x' }]);
    service.stop();
    expect(fake.deactivate).toHaveBeenCalled();
    expect(service.unreadCount()).toBe(0);
    expect(service.connected()).toBe(false);
  });
});
