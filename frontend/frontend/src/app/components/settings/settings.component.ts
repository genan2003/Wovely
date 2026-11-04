import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StorageService } from '../../services/storage.service';
import { UserService } from '../../services/user.service';
import { AddressBookComponent } from '../shared/address-book/address-book.component';
import { WalletPanelComponent } from '../shared/wallet-panel/wallet-panel.component';

@Component({
    selector: 'app-settings',
    standalone: true,
    imports: [CommonModule, FormsModule, AddressBookComponent, WalletPanelComponent],
    templateUrl: './settings.component.html',
    styleUrl: './settings.component.css'
})
export class SettingsComponent implements OnInit {
    private storageService = inject(StorageService);
    private userService = inject(UserService);

    public user = signal<any>(null);
    public username = computed(() => this.user()?.username || '');
    public isSeller = computed(() => this.user()?.roles?.includes('ROLE_SELLER'));

    // Form model
    public profile = {
        fullName: '',
        makerStory: '',
        workshopImageUrl: '',
        city: ''
    };

    ngOnInit(): void {
        const userData = this.storageService.getUser();
        this.user.set(userData);
        
        if (userData && userData.id) {
            this.loadProfileFromBackend(userData.id);
        }
    }

    loadProfileFromBackend(userId: string): void {
        this.userService.getUserProfile(userId).subscribe({
            next: (data) => {
                this.profile.fullName = data.fullName || data.username;
                this.profile.makerStory = data.makerStory || '';
                this.profile.workshopImageUrl = data.workshopImageUrl || '';
                this.profile.city = data.city || '';
                // Load other fields if they existed in backend
            },
            error: (err) => console.error('Failed to load profile', err)
        });
    }

    saveProfile(): void {
        const userData = this.user();
        if (!userData) return;

        this.userService.updateProfile(userData.id, {
            fullName: this.profile.fullName,
            makerStory: this.profile.makerStory,
            workshopImageUrl: this.profile.workshopImageUrl,
            city: this.profile.city
        }).subscribe({
            next: () => alert('Profile information saved successfully!'),
            error: (err) => alert('Failed to save profile: ' + err.message)
        });
    }

}
