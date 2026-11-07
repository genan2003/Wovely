import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, signal, viewChild } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, filter, forkJoin, map, of, switchMap } from 'rxjs';
import { AuthService } from './services/auth.service';
import { CartService } from './services/cart.service';
import { CategoryService } from './services/category.service';
import { RealtimeService } from './services/realtime.service';
import { ToastService } from './services/toast.service';
import { Category } from './models/category.model';
import { ToastOutletComponent } from './components/shared/toast-outlet/toast-outlet.component';
import { ConfirmDialogComponent } from './components/shared/confirm-dialog/confirm-dialog.component';

type CategoryGroup = Category & { children: Category[] };
type Menu = 'categories' | 'account';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ToastOutletComponent, ConfirmDialogComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(document:click)': 'onDocumentClick($event)',
    '(document:keydown.escape)': 'closeMenus()',
  },
})
export class App {
  private auth = inject(AuthService);
  private router = inject(Router);
  private host = inject<ElementRef<HTMLElement>>(ElementRef);
  private categoryService = inject(CategoryService);
  private toast = inject(ToastService);
  public cartService = inject(CartService);
  public realtime = inject(RealtimeService);

  public isLoggedIn = this.auth.isLoggedIn;
  public isAdmin = this.auth.isAdmin;
  public isSeller = this.auth.isSeller;
  public user = this.auth.user;

  public categories = signal<CategoryGroup[]>([]);
  public openMenu = signal<Menu | null>(null);
  private main = viewChild.required<ElementRef<HTMLElement>>('main');

  constructor() {
    this.loadCategories();
    // One real-time chat connection while signed in (PLAN.md 3.4)
    effect(() => (this.isLoggedIn() ? this.realtime.start() : this.realtime.stop()));
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.closeMenus());
  }

  private loadCategories(): void {
    this.categoryService
      .getRootCategories()
      .pipe(
        switchMap((roots) => {
          if (!roots || roots.length === 0) return of([]);
          return forkJoin(
            roots.map((root) =>
              this.categoryService.getSubCategories(root.id).pipe(
                map((children) => ({ ...root, children })),
                catchError(() => of({ ...root, children: [] })),
              ),
            ),
          );
        }),
        catchError(() => of([])), // The menu still offers "All products"
      )
      .subscribe((data) => this.categories.set(data));
  }

  toggleMenu(menu: Menu): void {
    this.openMenu.update((open) => (open === menu ? null : menu));
  }

  closeMenus(): void {
    this.openMenu.set(null);
  }

  onDocumentClick(event: Event): void {
    const header = this.host.nativeElement.querySelector('header');
    if (this.openMenu() && !header?.contains(event.target as Node)) {
      this.closeMenus();
    }
  }

  skipToContent(): void {
    this.main().nativeElement.focus();
  }

  logout(): void {
    this.closeMenus();
    this.auth.logout().subscribe({
      complete: () => {
        this.toast.info('You’re logged out.');
        this.router.navigate(['/products']);
      },
    });
  }
}
