import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { UserService } from '../../services/user.service';
import { ToastService } from '../../services/toast.service';
import { AddressBookComponent } from '../shared/address-book/address-book.component';
import { WalletPanelComponent } from '../shared/wallet-panel/wallet-panel.component';

@Component({
  selector: 'app-settings',
  imports: [ReactiveFormsModule, AddressBookComponent, WalletPanelComponent],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsComponent implements OnInit {
  private auth = inject(AuthService);
  private userService = inject(UserService);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder);

  public user = this.auth.user;
  public username = computed(() => this.user()?.username ?? '');
  public isSeller = this.auth.isSeller;

  public form = this.fb.nonNullable.group({
    fullName: ['', Validators.maxLength(100)],
    makerStory: ['', Validators.maxLength(2000)],
    workshopImageUrl: [''],
    city: ['', Validators.maxLength(100)],
  });

  ngOnInit(): void {
    const id = this.user()?.id;
    if (!id) return;
    this.userService.getUserProfile(id).subscribe({
      next: (data) =>
        this.form.reset({
          fullName: data.fullName || data.username || '',
          makerStory: data.makerStory ?? '',
          workshopImageUrl: data.workshopImageUrl ?? '',
          city: data.city ?? '',
        }),
      error: (err: unknown) => this.toast.apiError(err, 'Your profile couldn’t be loaded.'),
    });
  }

  saveProfile(): void {
    const id = this.user()?.id;
    if (!id || this.form.invalid) return;
    this.userService.updateProfile(id, this.form.getRawValue()).subscribe({
      next: () => {
        this.form.markAsPristine();
        this.toast.success('Profile saved.');
      },
      error: (err: unknown) => this.toast.apiError(err, 'Your profile wasn’t saved. Try again.'),
    });
  }
}
