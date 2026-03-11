export interface UserCrm {
  id: string;
  username: string;
  email: string;
  roles: string[];
  strikes: number;
  accountStatus: string;
  suspendedUntil?: Date;
}
