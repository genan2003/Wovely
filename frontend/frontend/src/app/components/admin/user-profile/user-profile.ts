import { ChangeDetectionStrategy, Component, inject, linkedSignal, input, signal, OnInit, output } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { AdminService } from '../../../services/admin.service';
import { PenaltyRequest, UserCrm } from '../../../models/user-crm.model';
import { ToastService } from '../../../services/toast.service';
import { Product } from '../../../models/product.model';

@Component({
  selector: 'app-user-profile',
  imports: [DecimalPipe, DatePipe],
  templateUrl: './user-profile.html',
  styleUrls: ['./user-profile.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserProfileComponent implements OnInit {
  readonly initialUser = input.required<UserCrm>({ alias: 'user' });
  /** Follows the input, then shows what the server returns after each penalty. */
  readonly user = linkedSignal(() => this.initialUser());
  readonly close = output<void>();

  private adminService = inject(AdminService);
  private toast = inject(ToastService);

  userAds = signal<Product[]>([]);
  isLoadingAds = signal(false);

  ngOnInit(): void {
    this.loadUserAds();
  }

  loadUserAds(): void {
    this.isLoadingAds.set(true);
    this.adminService.getSellerProducts(this.user().id).subscribe({
      next: (ads) => {
        this.userAds.set(ads || []);
        this.isLoadingAds.set(false);
      },
      error: (err: unknown) => {
        this.toast.apiError(err, 'This user’s listings couldn’t be loaded.');
        this.isLoadingAds.set(false);
      },
    });
  }

  /** Server accepts 1–365 days; a suspension always ends on its own. */
  readonly suspendOptions = [1, 3, 7, 14, 30, 90, 365] as const;
  readonly suspendDays = signal(7);

  issueStrike(): void {
    this.penalize({ action: 'STRIKE' });
  }

  suspendUser(days: number): void {
    this.penalize({ action: 'STATUS', newStatus: 'SUSPENDED', suspendDays: days });
  }

  banUser(): void {
    this.penalize({ action: 'STATUS', newStatus: 'BANNED' });
  }

  restoreUser(): void {
    this.penalize({ action: 'STATUS', newStatus: 'ACTIVE' });
  }

  private penalize(request: PenaltyRequest): void {
    this.adminService.applyPenalty(this.user().id, request).subscribe({
      next: (updatedUser) => this.user.set(updatedUser),
      error: (err: unknown) => this.toast.apiError(err, 'The account wasn’t updated. Try again.'),
    });
  }
}
