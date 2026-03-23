import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AdminService } from '../../services/admin.service';
import { Product } from '../../models/product.model';
import { UserCrmComponent } from './user-crm/user-crm';
import { OrderResolutionComponent } from './order-resolution/order-resolution.component';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, UserCrmComponent, OrderResolutionComponent],
  templateUrl: './admin.component.html',
  styleUrls: ['./admin.component.css']
})
export class AdminComponent implements OnInit {
  private adminService = inject(AdminService);
  private route = inject(ActivatedRoute);

  activeTab = signal<'ads' | 'users' | 'orders'>('ads');

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
    status: ''
  });

  ngOnInit(): void {
    // Check for tab query param
    const tab = this.route.snapshot.queryParamMap.get('tab');
    if (tab === 'users') {
      this.activeTab.set('users');
    } else if (tab === 'orders') {
      this.activeTab.set('orders');
    }
    this.loadPendingAds();
  }

  loadPendingAds(): void {
    this.adminService.getPendingAds().subscribe({
      next: (ads) => {
        this.pendingAds.set(ads || []);
      },
      error: (err) => console.error('Failed to load pending ads', err)
    });
  }

  approveAd(id: string): void {
    this.adminService.updateAdStatus(id, 'APPROVED').subscribe(() => this.loadPendingAds());
  }

  rejectAd(id: string): void {
    this.adminService.updateAdStatus(id, 'REJECTED').subscribe(() => this.loadPendingAds());
  }

  requestChanges(id: string): void {
    this.adminService.updateAdStatus(id, 'NEEDS_CHANGES').subscribe(() => this.loadPendingAds());
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
        error: (err) => console.error('Failed to save edits', err)
      });
    }
  }

  switchTab(tab: 'ads' | 'users' | 'orders'): void {
    this.activeTab.set(tab);
  }
}
