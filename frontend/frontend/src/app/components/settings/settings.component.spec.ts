import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { SettingsComponent } from './settings.component';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { provideTestDefaults } from '../../../testing/test-providers';
import { anAuthUser } from '../../../testing/fixtures';

describe('SettingsComponent', () => {
  let http: HttpTestingController;
  const isProfile = (r: { url: string }) => r.url === '/api/user/profile/seller-1';

  async function setup() {
    TestBed.configureTestingModule({ imports: [SettingsComponent], providers: provideTestDefaults() });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).user.set(anAuthUser({ id: 'seller-1', roles: ['ROLE_SELLER'] }));
    const fixture = TestBed.createComponent(SettingsComponent);
    await fixture.whenStable();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  it('shows the saved profile once it arrives (regression: zoneless forms stayed blank)', async () => {
    const { fixture, root } = await setup();
    http.expectOne(isProfile).flush({ fullName: 'Ana Pop', city: 'Sibiu' });
    await fixture.whenStable();

    expect(root.querySelector<HTMLInputElement>('#fullName')?.value).toBe('Ana Pop');
    expect(root.querySelector<HTMLInputElement>('#workshopCity')?.value).toBe('Sibiu');
  });

  it('saves changes and confirms with a toast', async () => {
    const { fixture } = await setup();
    http.expectOne(isProfile).flush({ fullName: 'Ana Pop' });
    await fixture.whenStable();

    const component = fixture.componentInstance;
    component.form.controls.city.setValue('Cluj');
    component.saveProfile();

    const save = http.expectOne((r) => isProfile(r) && r.method === 'PUT');
    expect(save.request.body).toMatchObject({ fullName: 'Ana Pop', city: 'Cluj' });
    save.flush({});
    expect(TestBed.inject(ToastService).toasts().map((t) => t.message)).toEqual(['Profile saved.']);
  });
});
