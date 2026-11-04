import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';

import { SellerProfileComponent } from './seller-profile.component';
import { provideTestDefaults } from '../../../testing/test-providers';
import { aSellerReview } from '../../../testing/fixtures';

/** PLAN.md 3.3 — the badge appears only on server-verified reviews. */
describe('SellerProfileComponent', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SellerProfileComponent],
      providers: [
        ...provideTestDefaults(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'seller-1' }) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('marks only verified seller reviews', () => {
    const fixture = TestBed.createComponent(SellerProfileComponent);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/api/public/users/seller/seller-1'))
      .flush({ id: 'seller-1', username: 'seller' });
    http.expectOne((r) => r.url.endsWith('/api/public/users/seller/seller-1/reviews')).flush([
      aSellerReview({ id: 'a', buyerName: 'verifiedbuyer', verifiedPurchase: true }),
      aSellerReview({ id: 'b', buyerName: 'chatonly', verifiedPurchase: false }),
    ]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const badges = el.querySelectorAll('app-verified-badge');
    expect(badges.length).toBe(1);
    expect(badges[0].closest('.review-item')?.textContent).toContain('verifiedbuyer');
    expect(fixture.componentInstance.averageRatings().overall).toBe(5);
  });
});
