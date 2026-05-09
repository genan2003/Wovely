import { Component, inject, signal, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser, CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from './services/auth.service';
import { StorageService } from './services/storage.service';

import { CartService } from './services/cart.service';
import { CategoryService } from './services/category.service';
import { Category } from './models/category.model';
import { forkJoin, map, switchMap, catchError, of } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private authService = inject(AuthService);
  private storageService = inject(StorageService);
  private router = inject(Router);
  public cartService = inject(CartService);
  private categoryService = inject(CategoryService);

  public categories = signal<any[]>([]);
  private platformId = inject(PLATFORM_ID);

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      this.loadCategories();
    }
  }

  loadCategories() {
    this.categoryService.getRootCategories().pipe(
      switchMap(roots => {
        if (!roots || roots.length === 0) return of([]);
        const obs = roots.map(root => 
          this.categoryService.getSubCategories(root.id).pipe(
            map(subs => ({ ...root, children: subs })),
            catchError(() => of({ ...root, children: [] }))
          )
        );
        return forkJoin(obs);
      }),
      catchError(err => {
        console.error('Failed to load categories', err);
        return of([]);
      })
    ).subscribe(data => {
      this.categories.set(data);
    });
  }

  // Use computed or the signal directly from authService
  public isLoggedIn = this.authService.isLoggedIn;

  isAdmin(): boolean {
    const user = this.storageService.getUser();
    return user && user.roles && user.roles.includes('ROLE_ADMIN');
  }

  isSeller(): boolean {
    const user = this.storageService.getUser();
    return user && user.roles && user.roles.includes('ROLE_SELLER');
  }

  filterByCategory(category: string, event: Event): void {
    event.preventDefault();
    this.router.navigate(['/products'], { queryParams: { categoryPath: category } });
  }

  logout(event: Event): void {
    event.preventDefault();
    this.authService.logout().subscribe();
  }
}
