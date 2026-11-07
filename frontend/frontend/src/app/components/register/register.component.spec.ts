import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { RegisterComponent } from './register.component';
import { provideTestDefaults } from '../../../testing/test-providers';

/** Sellers can sign up in the browser (they used to need a hand-made API call). */
describe('RegisterComponent', () => {
  let http: HttpTestingController;

  function setup() {
    TestBed.configureTestingModule({ imports: [RegisterComponent], providers: provideTestDefaults() });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    return fixture;
  }

  function fill(el: HTMLElement) {
    for (const [id, value] of [['username', 'mara'], ['email', 'mara@example.com'], ['password', 'secret123']]) {
      const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
  }

  afterEach(() => http.verify());

  it('signs up a buyer by default', () => {
    const fixture = setup();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector<HTMLInputElement>('input[value="buyer"]')!.checked).toBe(true);
    fill(el);
    el.querySelector('form')!.dispatchEvent(new Event('submit'));

    const req = http.expectOne((r) => r.url.endsWith('/auth/signup'));
    expect(req.request.body).toEqual({ username: 'mara', email: 'mara@example.com', password: 'secret123', roles: ['user'] });
    req.flush({ message: 'ok' });
  });

  it('signs up a maker and says what happens next', async () => {
    const fixture = setup();
    const el = fixture.nativeElement as HTMLElement;
    fill(el);
    const seller = el.querySelector<HTMLInputElement>('input[value="seller"]')!;
    seller.click();
    fixture.detectChanges();
    el.querySelector('form')!.dispatchEvent(new Event('submit'));

    const req = http.expectOne((r) => r.url.endsWith('/auth/signup'));
    expect(req.request.body.roles).toEqual(['seller']);
    req.flush({ message: 'ok' });
    await fixture.whenStable();
    expect(el.querySelector('[role="status"]')?.textContent).toContain('maker account is ready');
  });

  it('groups the choice under a visible legend', () => {
    const el = setup().nativeElement as HTMLElement;
    expect(el.querySelector('fieldset legend')?.textContent).toContain('How will you use Wovely?');
    expect(el.querySelectorAll('fieldset input[type="radio"][name]').length).toBe(2);
  });
});
