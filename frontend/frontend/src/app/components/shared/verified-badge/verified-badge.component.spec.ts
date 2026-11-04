import { TestBed } from '@angular/core/testing';
import { VerifiedBadgeComponent } from './verified-badge.component';

describe('VerifiedBadgeComponent', () => {
  function render(kind?: 'product' | 'seller') {
    const fixture = TestBed.createComponent(VerifiedBadgeComponent);
    if (kind) fixture.componentRef.setInput('kind', kind);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows readable text, not just an icon', () => {
    const el = render();
    expect(el.textContent).toContain('Verified purchase');
    expect(el.querySelector('i')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('explains a product purchase by default', () => {
    expect(render().querySelector('.wv-verified')?.getAttribute('title')).toContain('received this item');
  });

  it('explains a seller purchase', () => {
    const el = render('seller');
    expect(el.querySelector('.wv-verified')?.getAttribute('title')).toContain('from this seller');
    expect(el.querySelector('.visually-hidden')?.textContent).toContain('from this seller');
  });
});
