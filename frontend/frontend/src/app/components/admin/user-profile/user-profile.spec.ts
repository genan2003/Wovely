import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
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
    component.user = aUserCrm();
    fixture.detectChanges();
    http.match(() => true).forEach((req) => req.flush([]));
    expect(component).toBeTruthy();
  });

  it("should load the user's listings on init", () => {
    component.user = aUserCrm({ id: 'seller-42' });
    fixture.detectChanges();

    const req = http.expectOne((r) => r.url.includes('/seller/seller-42'));
    expect(component.isLoadingAds()).toBe(true);
    req.flush([{ id: 'p1', name: 'Woven basket' }]);

    expect(component.isLoadingAds()).toBe(false);
    expect(component.userAds().length).toBe(1);
  });

  it('should stop loading and keep an empty list when the request fails', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    component.user = aUserCrm({ id: 'seller-42' });
    fixture.detectChanges();

    http.expectOne((r) => r.url.includes('/seller/seller-42'))
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(component.isLoadingAds()).toBe(false);
    expect(component.userAds()).toEqual([]);
    expect(consoleError).toHaveBeenCalled();
    consoleError.mockRestore();
  });
});
