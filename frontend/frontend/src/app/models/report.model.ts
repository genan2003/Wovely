export type ReportTarget = 'CHAT' | 'PRODUCT';
export type ReportStatus = 'PENDING' | 'RESOLVED' | 'DISMISSED';

/** What the client may send; reporter, status and display name are set by the server (PLAN.md 3.2). */
export interface CreateReportRequest {
  targetType: ReportTarget;
  /** Product id, or for chats the other participant's user id. */
  targetId: string;
  reason: string;
}

export interface Report {
  id: string;
  reporterId: string;
  reporterName: string;
  targetType: ReportTarget;
  targetId: string;
  targetName?: string;
  reason: string;
  status: ReportStatus;
  createdAt?: string;
  participantIds?: string[];
}
