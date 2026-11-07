import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AdminService } from '../../services/admin.service';
import { Product } from '../../models/product.model';
import { UserCrmComponent } from './user-crm/user-crm';
import { OrderResolutionComponent } from './order-resolution/order-resolution.component';
import { AdminReturnsComponent } from './returns-management/returns-management.component';
import { FlaggedContentComponent } from './flagged-content/flagged-content.component';
import { AuditTrailComponent } from './audit-trail/audit-trail.component';
import { ToastService } from '../../services/toast.service';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { ImageFallbackDirective } from '../../directives/image-fallback.directive';

export type AdminTab = 'ads' | 'users' | 'orders' | 'returns' | 'flags' | 'audit';
const TABS: AdminTab[] = ['ads', 'users', 'orders', 'returns', 'flags', 'audit'];

@Component({
  selector: 'app-admin',
  imports: [DecimalPipe, 
    FormsModule,
    UserCrmComponent,
    OrderResolutionComponent,
    AdminReturnsComponent,
    FlaggedContentComponent,
    AuditTrailComponent,
    EmptyStateComponent,
    ImageFallbackDirective,
  ],
  templateUrl: './admin.component.html',
  styleUrls: ['./admin.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminComponent implements OnInit {
  private adminService = inject(AdminService);
  private route = inject(ActivatedRoute);
  private toast = inject(ToastService);

  readonly tabs: { id: AdminTab; label: string }[] = [
    { id: 'ads', label: 'Listings to approve' },
    { id: 'users', label: 'Users' },
    { id: 'orders', label: 'Orders' },
    { id: 'returns', label: 'Refunds' },
    { id: 'flags', label: 'Reports' },
    { id: 'audit', label: 'Audit trail' },
  ];
  activeTab = signal<AdminTab>('ads');

  pendingAds = signal<Product[]>([]);
  selectedAd = signal<Product | null>(null);

  isEditing = signal(false);
  editForm = signal<Product>({
    name: '',
    description: '',
    price: 0,
    sellerId: '',
    imageUrl: '',
    category: '',
    co2EmissionScore: '',
    shippingMethod: '',
    isHandmade: false,
    status: '',
  });

  ngOnInit(): void {
    // Check for tab query param
    const tab = this.route.snapshot.queryParamMap.get('tab') as AdminTab | null;
    if (tab && TABS.includes(tab)) {
      this.activeTab.set(tab);
    }
    this.loadPendingAds();
  }

  loadPendingAds(): void {
    this.adminService.getPendingAds().subscribe({
      next: (ads) => {
        this.pendingAds.set(ads || []);
      },
      error: (err: unknown) => this.toast.apiError(err, 'The approval queue couldn’t be loaded.'),
    });
  }

  approveAd(id: string): void {
    this.setAdStatus(id, 'APPROVED', 'Listing approved.');
  }

  rejectAd(id: string): void {
    this.setAdStatus(id, 'REJECTED', 'Listing rejected.');
  }

  requestChanges(id: string): void {
    this.setAdStatus(id, 'NEEDS_CHANGES', 'Changes requested from the seller.');
  }

  private setAdStatus(id: string, status: string, done: string): void {
    this.adminService.updateAdStatus(id, status).subscribe({
      next: () => {
        this.toast.success(done);
        this.loadPendingAds();
      },
      error: (err: unknown) => this.toast.apiError(err, 'The listing wasn’t updated. Try again.'),
    });
  }

  openEditModal(ad: Product): void {
    this.selectedAd.set(ad);
    this.editForm.set({ ...ad });
    this.isEditing.set(true);
  }

  closeEditModal(): void {
    this.isEditing.set(false);
    this.selectedAd.set(null);
  }

  saveEdits(): void {
    const adToSave = this.editForm();
    if (adToSave && adToSave.id) {
      this.adminService.updateAdContent(adToSave.id, adToSave).subscribe({
        next: () => {
          this.closeEditModal();
          this.loadPendingAds();
        },
        error: (err: unknown) => this.toast.apiError(err, 'Your edits weren’t saved. Try again.'),
      });
    }
  }

  switchTab(tab: AdminTab): void {
    this.activeTab.set(tab);
  }
}
