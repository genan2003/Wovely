import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree } from '@angular/router';

import { authGuard, roleGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { provideTestDefaults } from '../../testing/test-providers';
import { anAuthUser } from '../../testing/fixtures';
import { Role } from '../models/auth.model';

describe('route guards (PLAN.md 5.1)', () => {
  const run = (guard: CanActivateFn, url = '/seller/orders') =>
    TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
  const serialize = (result: unknown) => TestBed.inject(Router).serializeUrl(result as UrlTree);
  const signInAs = (...roles: Role[]) => TestBed.inject(AuthService).user.set(anAuthUser({ roles }));

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: provideTestDefaults() });
  });

  it('authGuard sends guests to /login and remembers where they were going', () => {
    expect(serialize(run(authGuard, '/settings'))).toBe('/login?returnUrl=%2Fsettings');
  });

  it('authGuard lets signed-in users through', () => {
    signInAs('ROLE_USER');
    expect(run(authGuard)).toBe(true);
  });

  it('roleGuard sends guests to /login', () => {
    expect(serialize(run(roleGuard('ROLE_SELLER')))).toBe('/login?returnUrl=%2Fseller%2Forders');
  });

  it('roleGuard keeps a buyer out of the seller area and says why', () => {
    signInAs('ROLE_USER');
    expect(serialize(run(roleGuard('ROLE_SELLER')))).toBe('/products');
    expect(TestBed.inject(ToastService).toasts().length).toBe(1);
  });

  it('roleGuard keeps a seller out of the admin area', () => {
    signInAs('ROLE_SELLER');
    expect(serialize(run(roleGuard('ROLE_ADMIN')))).toBe('/products');
  });

  it('roleGuard admits the matching role', () => {
    signInAs('ROLE_USER', 'ROLE_SELLER');
    expect(run(roleGuard('ROLE_SELLER'))).toBe(true);
  });
});
