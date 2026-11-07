import { TestBed } from '@angular/core/testing';

import { FavoriteService } from './favorite.service';
import { AuthService } from './auth.service';
import { provideTestDefaults } from '../../testing/test-providers';
import { aProduct, anAuthUser } from '../../testing/fixtures';

describe('FavoriteService', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: provideTestDefaults() });
  });

  it('follows login and logout instead of keeping the previous user’s favourites', () => {
    const auth = TestBed.inject(AuthService);
    const favorites = TestBed.inject(FavoriteService);

    auth.user.set(anAuthUser({ id: 'alice' }));
    TestBed.tick();
    favorites.toggleFavorite(aProduct());
    expect(favorites.isFavorite('p-1')).toBe(true);

    auth.user.set(null);
    TestBed.tick();
    expect(favorites.getFavorites()).toEqual([]);

    auth.user.set(anAuthUser({ id: 'bob' }));
    TestBed.tick();
    expect(favorites.isFavorite('p-1')).toBe(false);

    auth.user.set(anAuthUser({ id: 'alice' }));
    TestBed.tick();
    expect(favorites.isFavorite('p-1')).toBe(true);
  });

  it('toggles a favourite off again', () => {
    TestBed.inject(AuthService).user.set(anAuthUser());
    const favorites = TestBed.inject(FavoriteService);
    TestBed.tick();
    favorites.toggleFavorite(aProduct());
    favorites.toggleFavorite(aProduct());
    expect(favorites.getFavorites()).toEqual([]);
  });
});
