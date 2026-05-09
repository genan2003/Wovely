export interface UserCrm {
  id: string;
  username: string;
  fullName?: string;
  email: string;
  roles: string[];
  strikes: number;
  accountStatus: string;
  suspendedUntil?: any; // Changed from Date to any just to see if it helps re-trigger
}
