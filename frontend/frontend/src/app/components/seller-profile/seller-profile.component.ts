import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { PublicUserService } from '../../services/public-user.service';
import { SellerProfile } from '../../models/seller-profile.model';

@Component({
    selector: 'app-seller-profile',
    standalone: true,
    imports: [CommonModule, RouterModule],
    templateUrl: './seller-profile.component.html',
    styleUrl: './seller-profile.component.css'
})
export class SellerProfileComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private publicUserService = inject(PublicUserService);

    public seller = signal<SellerProfile | null>(null);
    public safeSeller = computed(() => this.seller() || ({} as SellerProfile));
    public reviews = signal<any[]>([]);
    public loading = signal<boolean>(true);

    public averageRatings = computed(() => {
        const msgs = this.reviews();
        if (msgs.length === 0) return { overall: 0, communication: 0, shipping: 0 };

        const total = msgs.length;
        const sumOverall = msgs.reduce((acc, m) => acc + m.overallRating, 0);
        const sumComm = msgs.reduce((acc, m) => acc + m.communicationRating, 0);
        const sumShip = msgs.reduce((acc, m) => acc + m.shippingRating, 0);

        return {
            overall: sumOverall / total,
            communication: sumComm / total,
            shipping: sumShip / total
        };
    });

    ngOnInit(): void {
        const sellerId = this.route.snapshot.paramMap.get('id');
        if (sellerId) {
            this.loadSellerData(sellerId);
        }
    }

    loadSellerData(id: string): void {
        this.loading.set(true);
        this.publicUserService.getSellerProfile(id).subscribe({
            next: (profile) => {
                this.seller.set(profile);
                this.loadReviews(id);
            },
            error: () => this.loading.set(false)
        });
    }

    loadReviews(id: string): void {
        this.publicUserService.getSellerReviews(id).subscribe({
            next: (msgs) => {
                this.reviews.set(msgs);
                this.loading.set(false);
            },
            error: () => this.loading.set(false)
        });
    }
}
