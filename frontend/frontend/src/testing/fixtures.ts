import { UserCrm } from '../app/models/user-crm.model';

/** Builders for test data. Override only the fields a test cares about. */
export function aUserCrm(overrides: Partial<UserCrm> = {}): UserCrm {
  return {
    id: 'u-1',
    username: 'artisan',
    fullName: 'Ana Artisan',
    email: 'ana@wovely.test',
    roles: ['ROLE_SELLER'],
    strikes: 0,
    accountStatus: 'ACTIVE',
    ...overrides,
  };
}
