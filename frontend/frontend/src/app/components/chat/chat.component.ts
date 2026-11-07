import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../../services/chat.service';
import { RealtimeService } from '../../services/realtime.service';
import { Subscription } from 'rxjs';
import { StorageService } from '../../services/storage.service';
import { ChatMessage } from '../../models/chat.model';
import { Product } from '../../models/product.model';
import { ImageFallbackDirective } from '../../directives/image-fallback.directive';

@Component({
  selector: 'app-chat',
  imports: [FormsModule, DatePipe, ImageFallbackDirective],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatComponent implements OnInit, OnDestroy {
  readonly receiverId = input.required<string>();
  readonly receiverName = input.required<string>();
  readonly product = input<Product>();
  readonly closeChat = output<void>();

  private chatService = inject(ChatService);
  private storageService = inject(StorageService);

  public currentUser = this.storageService.getUser()!;
  public messages = signal<ChatMessage[]>([]);
  public newMessage = signal('');
  private realtime = inject(RealtimeService);
  private realtimeSub?: Subscription;

  ngOnInit(): void {
    this.loadMessages();
    // Real-time updates replace the old 3 s polling (PLAN.md 3.4)
    this.realtimeSub = this.realtime.messages$.subscribe((m) => {
      const receiverId = this.receiverId();
      const mine =
        (m.senderId === this.currentUser.id && m.receiverId === receiverId) ||
        (m.senderId === receiverId && m.receiverId === this.currentUser.id);
      if (mine && !this.messages().some((x) => x.id && x.id === m.id)) {
        this.messages.update((list) => [...list, m]);
        this.scrollToBottom();
      }
    });
  }

  ngOnDestroy(): void {
    this.realtimeSub?.unsubscribe();
  }

  loadMessages(): void {
    this.chatService.getConversation(this.currentUser.id, this.receiverId()).subscribe((msgs) => {
      this.messages.set(msgs);
      this.scrollToBottom();
    });
  }

  send(): void {
    if (!this.newMessage().trim()) return;

    const msg: ChatMessage = {
      senderId: this.currentUser.id,
      receiverId: this.receiverId(),
      content: this.newMessage(),
      productId: this.product()?.id,
      productThumbnail: this.product()?.imageUrl,
    };

    this.chatService.sendMessage(msg).subscribe((savedMsg) => {
      this.messages.update((msgs) => [...msgs, savedMsg]);
      this.newMessage.set('');
      this.scrollToBottom();
    });
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const container = document.querySelector('.chat-messages');
      if (container) {
        container.scrollTop = container.scrollHeight;
      }
    }, 100);
  }
}
