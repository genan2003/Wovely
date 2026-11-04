import { TestBed } from '@angular/core/testing';
import { EmissionBadgeComponent } from './emission-badge.component';
import { DeliveryEmission } from '../../../models/delivery-emission';

describe('EmissionBadgeComponent', () => {
  function render(level: DeliveryEmission | null, compact = false): HTMLElement {
    const fixture = TestBed.createComponent(EmissionBadgeComponent);
    fixture.componentRef.setInput('level', level);
    fixture.componentRef.setInput('compact', compact);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('labels zero-emission delivery in full and compact form', () => {
    expect(render('ZERO').textContent?.trim()).toBe('Zero-emission delivery');
    expect(render('ZERO', true).textContent?.trim()).toBe('Zero-emission');
    expect(render('ZERO').querySelector('.wv-emission.zero')).not.toBeNull();
  });

  it('labels low-emission delivery', () => {
    const el = render('LOW');
    expect(el.textContent?.trim()).toBe('Low-emission delivery');
    expect(el.querySelector('.zero')).toBeNull();
  });

  it('renders nothing for standard or unknown delivery', () => {
    expect(render('STANDARD').querySelector('.wv-emission')).toBeNull();
    expect(render(null).querySelector('.wv-emission')).toBeNull();
  });
});
