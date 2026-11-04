import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { App } from './app';
import { provideTestDefaults } from '../testing/test-providers';

describe('App', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: provideTestDefaults(),
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    http.match(() => true).forEach((req) => req.flush([]));
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the Wovely brand in the navbar', async () => {
    const fixture = TestBed.createComponent(App);
    http.match(() => true).forEach((req) => req.flush([]));
    await fixture.whenStable();
    const brand = (fixture.nativeElement as HTMLElement).querySelector('.navbar-brand');
    expect(brand?.textContent?.trim()).toBe('Wovely');
  });

  it('should load root categories on start', () => {
    TestBed.createComponent(App);
    const req = http.expectOne((r) => r.url.endsWith('/api/categories/roots'));
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
