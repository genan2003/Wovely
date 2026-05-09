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
