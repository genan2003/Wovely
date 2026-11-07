import { TestBed } from '@angular/core/testing';
import { ListingStatusComponent } from './listing-status.component';

describe('ListingStatusComponent', () => {
  function render(status: string | null, always = false): HTMLElement {
    const fixture = TestBed.createComponent(ListingStatusComponent);
    fixture.componentRef.setInput('status', status);
    fixture.componentRef.setInput('always', always);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the review states with a readable hint', () => {
    const pending = render('PENDING');
    expect(pending.textContent).toContain('Awaiting review');
    expect(pending.querySelector('.wv-status.review')?.getAttribute('title')).toContain('Hidden from buyers');
    expect(render('NEEDS_CHANGES').querySelector('.changes')?.textContent).toContain('Changes requested');
    expect(render('REJECTED').querySelector('.rejected')?.textContent).toContain('Rejected');
  });

  it('stays quiet for live listings unless asked', () => {
    expect(render('APPROVED').querySelector('.wv-status')).toBeNull();
    expect(render('APPROVED', true).textContent).toContain('Live');
  });

  it('renders nothing for archived or unknown values', () => {
    expect(render('ARCHIVED').querySelector('.wv-status')).toBeNull();
    expect(render(null).querySelector('.wv-status')).toBeNull();
    expect(render('MAYBE').querySelector('.wv-status')).toBeNull();
  });

  it('hides the knot from assistive tech', () => {
    expect(render('PENDING').querySelector('.knot')?.getAttribute('aria-hidden')).toBe('true');
  });
});
