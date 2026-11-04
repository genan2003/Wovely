import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { UserCrmComponent } from './user-crm';
import { provideTestDefaults } from '../../../../testing/test-providers';
import { aUserCrm } from '../../../../testing/fixtures';

describe('UserCrmComponent', () => {
  let component: UserCrmComponent;
  let fixture: ComponentFixture<UserCrmComponent>;
  let http: HttpTestingController;

  const users = [
    aUserCrm({ id: '1', username: 'alice', email: 'alice@x.test', accountStatus: 'ACTIVE' }),
    aUserCrm({ id: '2', username: 'bob', email: 'bob@x.test', accountStatus: 'BANNED' }),
    aUserCrm({ id: '3', username: 'carol', email: 'carol@x.test', accountStatus: 'SUSPENDED' }),
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserCrmComponent],
      providers: provideTestDefaults(),
    }).compileComponents();

    fixture = TestBed.createComponent(UserCrmComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);

    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/api/admin/users')).flush(users);
  });

  afterEach(() => http.verify());

  it('should load all users on init', () => {
    expect(component.users().length).toBe(3);
    expect(component.filteredUsers().length).toBe(3);
  });

  it('should filter by username (case-insensitive)', () => {
    component.searchQuery.set('ALI');
    expect(component.filteredUsers().map((u) => u.username)).toEqual(['alice']);
  });

  it('should filter by account status', () => {
    component.statusFilter.set('BANNED');
    expect(component.filteredUsers().map((u) => u.username)).toEqual(['bob']);
  });

  it('should reset filters', () => {
    component.searchQuery.set('zzz');
    component.statusFilter.set('SUSPENDED');
    component.resetFilters();
    expect(component.filteredUsers().length).toBe(3);
  });
});
