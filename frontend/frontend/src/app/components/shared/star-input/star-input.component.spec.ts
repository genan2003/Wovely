import { TestBed } from '@angular/core/testing';
import { StarInputComponent } from './star-input.component';

describe('StarInputComponent', () => {
  function render(value = 0) {
    const fixture = TestBed.createComponent(StarInputComponent);
    fixture.componentRef.setInput('label', 'Overall experience');
    fixture.componentRef.setInput('value', value);
    fixture.detectChanges();
    return fixture;
  }

  it('is a named group of five radio buttons with spoken values', () => {
    const el = render().nativeElement as HTMLElement;
    expect(el.querySelector('legend')?.textContent).toContain('Overall experience');
    const radios = el.querySelectorAll<HTMLInputElement>('input[type="radio"]');
    expect(radios.length).toBe(5);
    expect(new Set([...radios].map((r) => r.name)).size).toBe(1);
    expect(el.textContent).toContain('3 of 5 stars');
  });

  it('updates the value when a star is chosen', () => {
    const fixture = render();
    const el = fixture.nativeElement as HTMLElement;
    el.querySelectorAll<HTMLInputElement>('input[type="radio"]')[3].click();
    fixture.detectChanges();
    expect(fixture.componentInstance.value()).toBe(4);
    expect(el.querySelectorAll('.star.on').length).toBe(4);
  });

  it('gives each picker its own radio group', () => {
    const a = render().nativeElement.querySelector('input').name;
    const b = render().nativeElement.querySelector('input').name;
    expect(a).not.toBe(b);
  });
});
