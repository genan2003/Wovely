import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { FlaggedContentComponent } from './flagged-content.component';
import { provideTestDefaults } from '../../../../testing/test-providers';
import { Report } from '../../../models/report.model';

/** PLAN.md 3.2 — moderation queue: only pending reports can be decided; chat logs per report. */
describe('FlaggedContentComponent', () => {
  let http: HttpTestingController;

  const report = (over: Partial<Report>): Report => ({
    id: 'r-1', reporterId: 'buyer-1', reporterName: 'testuser', targetType: 'CHAT',
    targetId: 'buyer-1:seller-1', targetName: 'Chat with seller', reason: 'spam', status: 'PENDING', ...over,
  });

  function setup(reports: Report[]) {
    TestBed.configureTestingModule({ imports: [FlaggedContentComponent], providers: provideTestDefaults() });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(FlaggedContentComponent);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/api/reports/all')).flush(reports);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  it('offers Resolve/Dismiss only on pending reports', () => {
    const fixture = setup([report({ id: 'a' }), report({ id: 'b', status: 'DISMISSED' })]);
    const cards = (fixture.nativeElement as HTMLElement).querySelectorAll('.card');
    expect(cards[0].textContent).toContain('Resolve');
    expect(cards[1].textContent).not.toContain('Resolve');
  });

  it('decides a report and reloads the queue', () => {
    const fixture = setup([report({})]);
    fixture.componentInstance.resolve('r-1', 'RESOLVED');
    const req = http.expectOne((r) => r.url.endsWith('/api/reports/r-1/status'));
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'RESOLVED' });
    req.flush(report({ status: 'RESOLVED' }));
    http.expectOne((r) => r.url.endsWith('/api/reports/all')).flush([report({ status: 'RESOLVED' })]);
    expect(fixture.componentInstance.reports()[0].status).toBe('RESOLVED');
  });

  it('shows the server message when a decision is rejected', () => {
    const fixture = setup([report({})]);
    fixture.componentInstance.resolve('r-1', 'DISMISSED');
    http.expectOne((r) => r.url.endsWith('/api/reports/r-1/status'))
      .flush({ message: 'Report is already RESOLVED' }, { status: 409, statusText: 'Conflict' });
    http.expectOne((r) => r.url.endsWith('/api/reports/all')).flush([report({ status: 'RESOLVED' })]);
    fixture.detectChanges();
    expect(fixture.componentInstance.actionError()).toBe('Report is already RESOLVED');
    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent).toContain('already RESOLVED');
  });

  it('loads the chat log of a chat report and names the participants', () => {
    const fixture = setup([report({})]);
    fixture.componentInstance.viewChatLog(report({}));
    http.expectOne((r) => r.url.endsWith('/api/reports/chat-log/r-1')).flush([
      { id: 'm1', senderId: 'buyer-1', receiverId: 'seller-1', content: 'hi' },
      { id: 'm2', senderId: 'seller-1', receiverId: 'buyer-1', content: 'go away' },
    ]);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('testuser:');
    expect(text).toContain('seller:');
    expect(text).toContain('go away');
  });
});
