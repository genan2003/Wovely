import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Client, IMessage } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import { ChatMessage } from '../models/chat.model';
import { StorageService } from './storage.service';
import { environment, wsUrl } from '../../environments/environment';

const CHAT_API = environment.apiUrl + '/chat';

/** Factory seam so tests can replace the STOMP client. */
export type StompClientFactory = (config: ConstructorParameters<typeof Client>[0]) => Pick<Client, 'activate' | 'deactivate' | 'subscribe' | 'connected'>;
export const defaultStompFactory: StompClientFactory = (config) => new Client(config);

/**
 * Real-time chat (PLAN.md 3.4): one STOMP connection per signed-in browser tab, replacing the old
 * 3–5 s polling. The JWT goes in the CONNECT frame (read fresh on every reconnect, so rotated tokens
 * work); the server only lets us subscribe to our own `/user/queue/messages`.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private storage = inject(StorageService);
  private http = inject(HttpClient);
  factory: StompClientFactory = defaultStompFactory;

  private client: ReturnType<StompClientFactory> | null = null;
  private readonly incoming = new Subject<ChatMessage>();

  /** Every chat message sent to or by me, as it happens. */
  readonly messages$ = this.incoming.asObservable();
  readonly connected = signal(false);
  /** Unread messages addressed to me (navbar badge). */
  readonly unreadCount = signal(0);

  start(): void {
    if (this.client || !this.storage.getToken()) return;
    this.refreshUnread();
    this.client = this.factory({
      brokerURL: wsUrl(),
      reconnectDelay: 5000,
      beforeConnect: (c) => {
        c.connectHeaders = { Authorization: `Bearer ${this.storage.getToken() ?? ''}` };
      },
      onConnect: () => {
        this.connected.set(true);
        this.client?.subscribe('/user/queue/messages', (frame: IMessage) => this.onFrame(frame));
      },
      onWebSocketClose: () => this.connected.set(false),
      onStompError: () => this.connected.set(false),
    });
    this.client.activate();
  }

  stop(): void {
    this.client?.deactivate();
    this.client = null;
    this.connected.set(false);
    this.unreadCount.set(0);
  }

  /** Re-read the unread count from the server (e.g. after a conversation was opened and marked read). */
  refreshUnread(): void {
    const me = this.storage.getUser()?.id;
    if (!me) return;
    this.http.get<ChatMessage[]>(`${CHAT_API}/unread/${me}`).subscribe({
      next: (list) => this.unreadCount.set(list?.length ?? 0),
      error: () => {},
    });
  }

  private onFrame(frame: IMessage): void {
    let msg: ChatMessage;
    try {
      msg = JSON.parse(frame.body) as ChatMessage;
    } catch {
      return;
    }
    if (msg.receiverId === this.storage.getUser()?.id) {
      this.unreadCount.update((n) => n + 1);
    }
    this.incoming.next(msg);
  }
}
