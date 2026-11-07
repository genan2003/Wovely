import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { UserProfileComponent } from './user-profile';
import { provideTestDefaults } from '../../../../testing/test-providers';
import { aUserCrm } from '../../../../testing/fixtures';

describe('UserProfileComponent', () => {
  let component: UserProfileComponent;
  let fixture: ComponentFixture<UserProfileComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserProfileComponent],
      providers: provideTestDefaults(),
    }).compileComponents();

    fixture = TestBed.createComponent(UserProfileComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should create', () => {
    fixture.componentRef.setInput('user', aUserCrm());
    fixture.detectChanges();
    http.match(() => true).forEach((req) => req.flush([]));
    expect(component).toBeTruthy();
  });

  it("should load the user's listings on init", () => {
    fixture.componentRef.setInput('user', aUserCrm({ id: 'seller-42' }));
    fixture.detectChanges();

    const req = http.expectOne((r) => r.url.includes('/seller/seller-42'));
    expect(component.isLoadingAds()).toBe(true);
    req.flush([{ id: 'p1', name: 'Woven basket' }]);

    expect(component.isLoadingAds()).toBe(false);
    expect(component.userAds().length).toBe(1);
  });

  it('should stop loading and keep an empty list when the request fails', () => {
    fixture.componentRef.setInput('user', aUserCrm({ id: 'seller-42' }));
    fixture.detectChanges();

    http.expectOne((r) => r.url.includes('/seller/seller-42'))
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(component.isLoadingAds()).toBe(false);
    expect(component.userAds()).toEqual([]);
  });

  it('shows the account the server returns after a penalty', async () => {
    fixture.componentRef.setInput('user', aUserCrm({ id: 'seller-42', strikes: 0 }));
    fixture.detectChanges();
    http.expectOne((r) => r.url.includes('/seller/seller-42')).flush([]);

    component.issueStrike();
    const req = http.expectOne((r) => r.url.endsWith('/seller-42/penalty'));
    expect(req.request.body).toEqual({ action: 'STRIKE' });
    req.flush(aUserCrm({ id: 'seller-42', strikes: 1 }));
    await fixture.whenStable();

    expect(component.user().strikes).toBe(1);
    expect((fixture.nativeElement as HTMLElement).querySelector('.text-danger')?.textContent).toContain('1');
  });

  it('suspends for the length the admin picks', () => {
    fixture.componentRef.setInput('user', aUserCrm({ id: 'seller-42' }));
    fixture.detectChanges();
    http.expectOne((r) => r.url.includes('/seller/seller-42')).flush([]);
    const el = fixture.nativeElement as HTMLElement;

    const select = el.querySelector<HTMLSelectElement>('#suspendDays')!;
    expect(el.querySelector('label[for="suspendDays"]')?.textContent).toContain('Suspension length');
    select.value = '30';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const button = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Suspend for'))!;
    expect(button.textContent).toContain('Suspend for 30 days');
    button.click();
    const req = http.expectOne((r) => r.url.endsWith('/seller-42/penalty'));
    expect(req.request.body).toEqual({ action: 'STATUS', newStatus: 'SUSPENDED', suspendDays: 30 });
    req.flush(aUserCrm({ id: 'seller-42' }));
  });
});
