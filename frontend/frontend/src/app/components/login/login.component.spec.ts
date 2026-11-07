import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { vi } from 'vitest';

import { LoginComponent } from './login.component';
import { provideTestDefaults } from '../../../testing/test-providers';

describe('LoginComponent', () => {
  let http: HttpTestingController;

  function setup(returnUrl?: string) {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        ...provideTestDefaults(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(returnUrl ? { returnUrl } : {}) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.form.setValue({ username: 'testuser', password: 'secret' });
    return { fixture, component, navigate };
  }

  const fail = () =>
    http.expectOne('/api/auth/signin').flush({ message: 'Bad credentials' }, { status: 401, statusText: 'Unauthorized' });
  const succeed = () =>
    http.expectOne('/api/auth/signin').flush({ id: 'b-1', username: 'testuser', accessToken: 't', roles: [] });

  it('lets the user try again after a wrong password', async () => {
    const { fixture, component } = setup();
    component.onSubmit();
    fail();
    await fixture.whenStable();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[role=alert]')?.textContent).toContain('Bad credentials');
    expect(root.querySelector('button')?.disabled).toBe(false);

    component.onSubmit();
    succeed();
  });

  it('only blames the password for a real 401', async () => {
    const { fixture, component } = setup();
    component.onSubmit();
    http.expectOne('/api/auth/signin').flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent).toContain('Try again');
    expect(component.errorMessage()).not.toContain('password');
  });

  it('returns to the page the guard interrupted', () => {
    const { component, navigate } = setup('/settings');
    component.onSubmit();
    succeed();
    expect(navigate).toHaveBeenCalledWith('/settings');
  });

  it('defaults to the catalog', () => {
    const { component, navigate } = setup();
    component.onSubmit();
    succeed();
    expect(navigate).toHaveBeenCalledWith('/products');
  });

  it.each(['https://evil.example', '//evil.example'])('ignores an off-site returnUrl: %s', (url) => {
    const { component, navigate } = setup(url);
    component.onSubmit();
    succeed();
    expect(navigate).toHaveBeenCalledWith('/products');
  });
});
