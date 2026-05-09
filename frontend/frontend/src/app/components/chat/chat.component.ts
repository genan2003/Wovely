import { Component, Input, OnInit, inject, signal, EventEmitter, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../../services/chat.service';
import { StorageService } from '../../services/storage.service';
import { ChatMessage } from '../../models/chat.model';
import { Product } from '../../models/product.model';

@Component({
    selector: 'app-chat',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './chat.component.html',
    styleUrl: './chat.component.css'
})
export class ChatComponent implements OnInit, OnDestroy {
    @Input() receiverId!: string;
    @Input() receiverName!: string;
    @Input() product?: Product;
    @Input() closeChat = new EventEmitter<void>();

    private chatService = inject(ChatService);
    private storageService = inject(StorageService);

    public currentUser = this.storageService.getUser();
    public messages = signal<ChatMessage[]>([]);
    public newMessage = '';
    public isTyping = signal<boolean>(false);
    private pollingInterval: any;

    ngOnInit(): void {
        this.loadMessages();
        this.startPolling();
    }

    ngOnDestroy(): void {
        if (this.pollingInterval) {
            clearInterval(this.pollingInterval);
        }
    }

    loadMessages(): void {
        this.chatService.getConversation(this.currentUser.id, this.receiverId).subscribe(msgs => {
            this.messages.set(msgs);
            this.scrollToBottom();
        });
    }

    startPolling(): void {
        this.pollingInterval = setInterval(() => {
            this.loadMessages();
        }, 3000);
    }

    send(): void {
        if (!this.newMessage.trim()) return;

        const msg: ChatMessage = {
            senderId: this.currentUser.id,
            receiverId: this.receiverId,
            content: this.newMessage,
            productId: this.product?.id,
            productThumbnail: this.product?.imageUrl
        };

        this.chatService.sendMessage(msg).subscribe(savedMsg => {
            this.messages.update(msgs => [...msgs, savedMsg]);
            this.newMessage = '';
            this.scrollToBottom();
        });
    }

    onType(): void {
        // Implement typing indicator logic here if backend supports it
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
