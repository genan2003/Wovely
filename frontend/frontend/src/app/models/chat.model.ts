/** One row of the inbox: the latest message with one other person. */
export interface Conversation {
    otherId: string;
    otherName: string;
    lastMessage: string;
    timestamp?: Date;
    isRead: boolean;
    avatar: string;
}

export interface ChatMessage {
    id?: string;
    senderId: string;
    receiverId: string;
    productId?: string;
    productThumbnail?: string;
    content: string;
    timestamp?: Date;
    isRead?: boolean;
}
