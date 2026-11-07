import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError, debounceTime, of, switchMap, tap } from 'rxjs';
import { AdminService } from '../../../services/admin.service';
import { AccountStatusFilter, UserCrm } from '../../../models/user-crm.model';
import { UserProfileComponent } from '../user-profile/user-profile';

/** Debounce for the CRM search box, so typing doesn't fire one request per key. */
export const USER_SEARCH_DEBOUNCE_MS = 250;

@Component({
  selector: 'app-user-crm',
  imports: [DatePipe, FormsModule, UserProfileComponent],
  templateUrl: './user-crm.html',
  styleUrls: ['./user-crm.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserCrmComponent {
  private adminService = inject(AdminService);

  users = signal<UserCrm[]>([]);
  loading = signal(true);
  loadError = signal(false);
  selectedUser = signal<UserCrm | null>(null);
  searchQuery = signal('');
  statusFilter = signal<AccountStatusFilter>('ALL');
  private reloadTick = signal(0);

  /** Search runs on the server (PLAN.md 3.1); the table shows exactly what it returns. */
  private filters = computed(() => ({
    query: this.searchQuery(),
    status: this.statusFilter(),
    tick: this.reloadTick(),
  }));

  constructor() {
    toObservable(this.filters)
      .pipe(
        debounceTime(USER_SEARCH_DEBOUNCE_MS),
        tap(() => this.loading.set(true)),
        switchMap(({ query, status }) =>
          this.adminService.searchUsers(query, status).pipe(
            tap(() => this.loadError.set(false)),
            catchError(() => {
              this.loadError.set(true);
              return of([] as UserCrm[]);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((users) => {
        this.users.set(users ?? []);
        this.loading.set(false);
      });
  }

  loadUsers(): void {
    this.reloadTick.update((n) => n + 1);
  }

  viewProfile(user: UserCrm): void {
    this.selectedUser.set(user);
  }

  closeProfile(): void {
    this.selectedUser.set(null);
    this.loadUsers();
  }

  resetFilters(): void {
    this.searchQuery.set('');
    this.statusFilter.set('ALL');
  }
}
