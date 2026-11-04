import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { AddressBookComponent } from './address-book.component';
import { provideTestDefaults } from '../../../../testing/test-providers';
import { Address } from '../../../models/address.model';

describe('AddressBookComponent', () => {
  let http: HttpTestingController;
  const URL = '/api/user/addresses';

  const home: Address = {
    id: 'a1', label: 'Home', recipientName: 'Ana Pop', street: 'Str. Florilor 3', city: 'Sibiu',
    postalCode: '550000', country: 'Romania', default: true,
  };
  const studio: Address = { ...home, id: 'a2', label: 'Studio', default: false };

  function setup(list: Address[]) {
    TestBed.configureTestingModule({ imports: [AddressBookComponent], providers: provideTestDefaults() });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AddressBookComponent);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith(URL) && r.method === 'GET').flush(list);
    fixture.detectChanges();
    return fixture;
  }

  const el = (f: { nativeElement: unknown }) => f.nativeElement as HTMLElement;
  const button = (f: { nativeElement: unknown }, text: string) =>
    [...el(f).querySelectorAll('button')].find((b) => b.textContent?.trim() === text) as HTMLButtonElement;

  afterEach(() => http.verify());

  it('invites the user to add a first address', () => {
    const f = setup([]);
    expect(el(f).textContent).toContain("You haven't saved an address yet");
  });

  it('marks the default and offers "Make default" only on the others', () => {
    const f = setup([home, studio]);
    const entries = el(f).querySelectorAll('.book-entry');
    expect(entries[0].textContent).toContain('Default');
    expect(entries[0].textContent).not.toContain('Make default');
    expect(entries[1].textContent).toContain('Make default');
  });

  it('validates before saving and posts a valid address', () => {
    const f = setup([]);
    button(f, 'Add an address').click();
    f.detectChanges();
    f.componentInstance.save();
    f.detectChanges();
    expect(el(f).textContent).toContain('Enter the street and number.');
    http.expectNone((r) => r.method === 'POST');

    f.componentInstance.form.patchValue({ recipientName: 'Ana Pop', street: 'Str. Florilor 3', city: 'Sibiu', phone: '+40 712' });
    f.componentInstance.save();
    const req = http.expectOne((r) => r.url.endsWith(URL) && r.method === 'POST');
    expect(req.request.body).toMatchObject({ recipientName: 'Ana Pop', country: 'Romania', makeDefault: true });
    req.flush(home);
    http.expectOne((r) => r.url.endsWith(URL) && r.method === 'GET').flush([home]);
    expect(f.componentInstance.editing()).toBeNull();
  });

  it('rejects a phone with letters', () => {
    const f = setup([]);
    f.componentInstance.startAdd();
    f.componentInstance.form.patchValue({ recipientName: 'A', street: 'S', city: 'C', phone: 'call me' });
    expect(f.componentInstance.form.controls.phone.invalid).toBe(true);
  });

  it('asks for confirmation inline before deleting', () => {
    const f = setup([home, studio]);
    const entry = el(f).querySelectorAll('.book-entry')[1];
    ([...entry.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Delete') as HTMLButtonElement).click();
    f.detectChanges();
    expect(el(f).textContent).toContain('Delete this address?');
    http.expectNone((r) => r.method === 'DELETE');

    f.componentInstance.remove(studio);
    http.expectOne((r) => r.url.endsWith(`${URL}/a2`) && r.method === 'DELETE').flush(null);
    http.expectOne((r) => r.url.endsWith(URL) && r.method === 'GET').flush([home]);
  });

  it('shows the server message when saving fails', () => {
    const f = setup([home]);
    f.componentInstance.startEdit(home);
    f.componentInstance.save();
    http.expectOne((r) => r.url.endsWith(`${URL}/a1`) && r.method === 'PUT')
      .flush({ message: 'You can save at most 10 addresses' }, { status: 409, statusText: 'Conflict' });
    f.detectChanges();
    expect(el(f).querySelector('[role=alert]')?.textContent).toContain('at most 10');
  });
});
