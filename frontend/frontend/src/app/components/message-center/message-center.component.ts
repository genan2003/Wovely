import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../../services/chat.service';
import { RealtimeService } from '../../services/realtime.service';
import { Subscription } from 'rxjs';
import { StorageService } from '../../services/storage.service';
import { PublicUserService } from '../../services/public-user.service';
import { AdminService } from '../../services/admin.service';
import { ChatMessage, Conversation } from '../../models/chat.model';
import { DialogService } from '../../services/dialog.service';
import { ImageFallbackDirective } from '../../directives/image-fallback.directive';
import { ToastService } from '../../services/toast.service';
import { forkJoin, map, of, catchError } from 'rxjs';

@Component({
  selector: 'app-message-center',
  imports: [FormsModule, DatePipe, ImageFallbackDirective],
  templateUrl: './message-center.component.html',
  styleUrl: './message-center.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageCenterComponent implements OnInit, OnDestroy {
  private chatService = inject(ChatService);
  private storageService = inject(StorageService);
  private publicUserService = inject(PublicUserService);
  private adminService = inject(AdminService);
  private dialog = inject(DialogService);
  private toast = inject(ToastService);

  public currentUser = this.storageService.getUser()!;
  public conversations = signal<Conversation[]>([]);
  public selectedConversation = signal<Conversation | null>(null);
  public messages = signal<ChatMessage[]>([]);
  public newMessage = signal('');
  public loading = signal<boolean>(true);

  private realtime = inject(RealtimeService);
  private realtimeSub?: Subscription;

  ngOnInit(): void {
    this.loadConversations();
    // Real-time updates replace the old 5 s polling (PLAN.md 3.4)
    this.realtimeSub = this.realtime.messages$.subscribe((m) => {
      const open = this.selectedConversation();
      const other = m.senderId === this.currentUser.id ? m.receiverId : m.senderId;
      if (open && open.otherId === other) {
        if (!this.messages().some((x: ChatMessage) => x.id && x.id === m.id)) {
          this.messages.update((list) => [...list, m]);
          this.scrollToBottom();
        }
        if (m.receiverId === this.currentUser.id) {
          this.loadMessages(other); // fetching marks it read on the server
        }
      }
      this.loadConversations();
    });
  }

  ngOnDestroy(): void {
    this.realtimeSub?.unsubscribe();
  }

  loadConversations(): void {
    this.chatService.getConversations(this.currentUser.id).subscribe((msgs) => {
      const conversationRequests = msgs.map((m) => {
        const otherId = m.senderId === this.currentUser.id ? m.receiverId : m.senderId;
        return this.publicUserService.getSellerProfile(otherId).pipe(
          map((profile) => ({
            otherId: otherId,
            otherName: profile.username,
            lastMessage: m.content,
            timestamp: m.timestamp,
            isRead: m.isRead || m.senderId === this.currentUser.id,
            avatar: profile.workshopImageUrl || 'avatar.svg',
          })),
          catchError(() =>
            of({
              otherId: otherId,
              otherName: 'User #' + otherId.substring(0, 6),
              lastMessage: m.content,
              timestamp: m.timestamp,
              isRead: m.isRead || m.senderId === this.currentUser.id,
              avatar: 'avatar.svg',
            }),
          ),
        );
      });

      if (conversationRequests.length > 0) {
        forkJoin(conversationRequests).subscribe((data) => {
          this.conversations.set(data);
          this.loading.set(false);
        });
      } else {
        this.loading.set(false);
      }
    });
  }

  selectConversation(conv: Conversation): void {
    this.selectedConversation.set(conv);
    this.loadMessages(conv.otherId);
  }

  loadMessages(otherId: string): void {
    this.chatService.getConversation(this.currentUser.id, otherId).subscribe((msgs) => {
      this.messages.set(msgs);
      this.scrollToBottom();
      this.realtime.refreshUnread();
    });
  }

  send(): void {
    const conv = this.selectedConversation();
    if (!this.newMessage().trim() || !conv) return;

    const msg: ChatMessage = {
      senderId: this.currentUser.id,
      receiverId: conv.otherId,
      content: this.newMessage(),
    };

    this.chatService.sendMessage(msg).subscribe((savedMsg) => {
      this.messages.update((msgs) => [...msgs, savedMsg]);
      this.newMessage.set('');
      this.scrollToBottom();

      // Update last message in sidebar
      this.conversations.update((list) =>
        list.map((c) =>
          c.otherId === conv.otherId
            ? { ...c, lastMessage: savedMsg.content, timestamp: savedMsg.timestamp }
            : c,
        ),
      );
    });
  }

  async reportConversation(): Promise<void> {
    const conv = this.selectedConversation();
    if (!conv) return;

    const reason = await this.dialog.prompt({
      title: `Report conversation with ${conv.otherName}`,
      message: 'A moderator will read this conversation and your reason.',
      label: 'What went wrong?',
      confirmLabel: 'Send report',
    });
    if (!reason) return;

    this.adminService
      .createReport({ targetType: 'CHAT', targetId: conv.otherId, reason })
      .subscribe({
        next: () => this.toast.success('Report sent. A moderator will review it.'),
        error: (err: unknown) => this.toast.apiError(err, 'The report wasn’t sent. Try again.'),
      });
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const container = document.querySelector('.chat-history');
      if (container) {
        container.scrollTop = container.scrollHeight;
      }
    }, 100);
  }
}
