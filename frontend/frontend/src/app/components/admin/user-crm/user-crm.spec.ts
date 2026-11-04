import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { vi } from 'vitest';

import { USER_SEARCH_DEBOUNCE_MS, UserCrmComponent } from './user-crm';
import { provideTestDefaults } from '../../../../testing/test-providers';
import { aUserCrm } from '../../../../testing/fixtures';

/** PLAN.md 3.1 — the CRM searches on the server, debounced. */
describe('UserCrmComponent', () => {
  let component: UserCrmComponent;
  let fixture: ComponentFixture<UserCrmComponent>;
  let http: HttpTestingController;

  const users = [
    aUserCrm({ id: '1', username: 'alice', email: 'alice@x.test', accountStatus: 'ACTIVE' }),
    aUserCrm({ id: '2', username: 'bob', email: 'bob@x.test', accountStatus: 'BANNED' }),
  ];

  const isUsersCall = (r: { url: string }) => r.url.endsWith('/api/admin/users');

  /** Let the signal → observable bridge emit, then elapse the debounce. */
  function settle(): void {
    TestBed.tick();
    vi.advanceTimersByTime(USER_SEARCH_DEBOUNCE_MS + 1);
  }

  function nextRequest(): TestRequest {
    settle();
    return http.expectOne(isUsersCall);
  }

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [UserCrmComponent],
      providers: provideTestDefaults(),
    }).compileComponents();

    fixture = TestBed.createComponent(UserCrmComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('loads every user on init without filter params', () => {
    const req = nextRequest();
    expect(req.request.params.keys()).toEqual([]);
    req.flush(users);
    expect(component.users().length).toBe(2);
    expect(component.loading()).toBe(false);
  });

  it('sends the trimmed query and the status filter to the server', () => {
    nextRequest().flush(users);
    component.searchQuery.set('  ali ');
    component.statusFilter.set('BANNED');
    const req = nextRequest();
    expect(req.request.params.get('query')).toBe('ali');
    expect(req.request.params.get('status')).toBe('BANNED');
    req.flush([users[1]]);
    expect(component.users().map((u) => u.username)).toEqual(['bob']);
  });

  it('debounces typing into a single request', () => {
    nextRequest().flush(users);
    for (const q of ['a', 'al', 'ali']) {
      component.searchQuery.set(q);
      TestBed.tick();
      vi.advanceTimersByTime(50);
    }
    const req = nextRequest();
    expect(req.request.params.get('query')).toBe('ali');
    req.flush([]);
  });

  it('shows an error state when the search fails', () => {
    nextRequest().flush('boom', { status: 500, statusText: 'Server Error' });
    expect(component.loadError()).toBe(true);
    expect(component.users()).toEqual([]);
  });

  it('reset clears both filters and re-queries without params', () => {
    nextRequest().flush(users);
    component.searchQuery.set('zzz');
    component.statusFilter.set('SUSPENDED');
    nextRequest().flush([]);
    component.resetFilters();
    const req = nextRequest();
    expect(req.request.params.keys()).toEqual([]);
    req.flush(users);
  });

  it('reloads after closing a profile', () => {
    nextRequest().flush(users);
    component.viewProfile(users[0]);
    component.closeProfile();
    nextRequest().flush(users);
    expect(component.selectedUser()).toBeNull();
  });
});
