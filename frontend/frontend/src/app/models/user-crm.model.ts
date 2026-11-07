export interface UserCrm {
  id: string;
  username: string;
  fullName?: string;
  email: string;
  roles: string[];
  strikes: number;
  accountStatus: string;
  suspendedUntil?: string;
}

/** Body of POST /api/admin/users/{id}/penalty. */
export interface PenaltyRequest {
  action: 'STRIKE' | 'STATUS';
  newStatus?: 'ACTIVE' | 'SUSPENDED' | 'BANNED';
  suspendDays?: number;
}

export interface AuditLog {
  id: string;
  timestamp: string;
  adminUsername: string;
  actionType: string;
  targetId: string;
  details: string;
}

export type AccountStatusFilter = 'ALL' | 'ACTIVE' | 'SUSPENDED' | 'BANNED';
