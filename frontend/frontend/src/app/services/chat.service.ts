import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChatMessage } from '../models/chat.model';

const API_URL = 'http://localhost:8081/api/chat';

@Injectable({
    providedIn: 'root'
})
export class ChatService {
    private http = inject(HttpClient);

    getConversation(user1: string, user2: string): Observable<ChatMessage[]> {
        return this.http.get<ChatMessage[]>(`${API_URL}/conversation?user1=${user1}&user2=${user2}`);
    }

    sendMessage(message: ChatMessage): Observable<ChatMessage> {
        return this.http.post<ChatMessage>(`${API_URL}/send`, message);
    }

    getUnreadMessages(userId: string): Observable<ChatMessage[]> {
        return this.http.get<ChatMessage[]>(`${API_URL}/unread/${userId}`);
    }

    getConversations(userId: string): Observable<ChatMessage[]> {
        return this.http.get<ChatMessage[]>(`${API_URL}/conversations/${userId}`);
    }
}
