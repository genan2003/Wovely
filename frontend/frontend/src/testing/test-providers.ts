import { EnvironmentProviders, Provider } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Routes } from '@angular/router';

/**
 * Default providers for component/service specs:
 * a real HttpClient backed by HttpTestingController, and an in-memory router.
 *
 *   TestBed.configureTestingModule({ imports: [MyComponent], providers: provideTestDefaults() });
 *   const http = TestBed.inject(HttpTestingController);
 */
export function provideTestDefaults(routes: Routes = []): (Provider | EnvironmentProviders)[] {
  return [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)];
}
