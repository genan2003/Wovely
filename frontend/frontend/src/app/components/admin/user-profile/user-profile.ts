import { Component, Input, Output, EventEmitter, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdminService } from '../../../services/admin.service';
import { UserCrm } from '../../../models/user-crm.model';
import { Product } from '../../../models/product.model';

@Component({
  selector: 'app-user-profile',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './user-profile.html',
  styleUrls: ['./user-profile.css']
})
export class UserProfileComponent implements OnInit {
  @Input() user!: UserCrm;
  @Output() close = new EventEmitter<void>();

  private adminService = inject(AdminService);
  
  userAds = signal<Product[]>([]);
  isLoadingAds = signal(false);

  ngOnInit(): void {
    if (this.user && this.user.id) {
      this.loadUserAds();
    }
  }

  loadUserAds(): void {
    this.isLoadingAds.set(true);
    this.adminService.getSellerProducts(this.user.id).subscribe({
      next: (ads) => {
        this.userAds.set(ads || []);
        this.isLoadingAds.set(false);
      },
      error: (err) => {
        console.error('Failed to load user ads', err);
        this.isLoadingAds.set(false);
      }
    });
  }

  issueStrike(): void {
    this.adminService.applyPenalty(this.user.id, { action: 'STRIKE' }).subscribe({
      next: (updatedUser) => this.user = updatedUser,
      error: (err) => console.error('Failed to issue strike', err)
    });
  }

  suspendUser(days: number): void {
    this.adminService.applyPenalty(this.user.id, { action: 'STATUS', newStatus: 'SUSPENDED', suspendDays: days }).subscribe({
      next: (updatedUser) => this.user = updatedUser,
      error: (err) => console.error('Failed to suspend user', err)
    });
  }

  banUser(): void {
    this.adminService.applyPenalty(this.user.id, { action: 'STATUS', newStatus: 'BANNED' }).subscribe({
      next: (updatedUser) => this.user = updatedUser,
      error: (err) => console.error('Failed to ban user', err)
    });
  }

  restoreUser(): void {
    this.adminService.applyPenalty(this.user.id, { action: 'STATUS', newStatus: 'ACTIVE' }).subscribe({
      next: (updatedUser) => this.user = updatedUser,
      error: (err) => console.error('Failed to restore user', err)
    });
  }
}
