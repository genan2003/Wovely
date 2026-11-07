import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { apiErrorMessage } from '../../services/toast.service';

type Field = 'username' | 'email' | 'password';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth">
      <h1>Create your account</h1>
      <p class="lede">Buy from independent makers, or sell what you make.</p>

      @if (isSuccessful()) {
        <div class="alert alert-success" role="status">
          @if (form.controls.accountKind.value === 'seller') {
            Your maker account is ready. Log in to add your first listing.
          } @else {
            Your account is ready.
          }
        </div>
        <a class="btn btn-primary" routerLink="/login" [queryParams]="{ returnUrl: returnUrl }">Log in</a>
      } @else {
        <form [formGroup]="form" (ngSubmit)="onSubmit()">
          <div class="field">
            <label for="username" class="form-label">Username</label>
            <input type="text" class="form-control" id="username" formControlName="username" autocomplete="username"
                   [attr.aria-invalid]="!!error('username')" [attr.aria-describedby]="error('username') ? 'username-error' : null" />
            @if (error('username'); as message) {
              <p class="field-error" id="username-error">{{ message }}</p>
            }
          </div>

          <div class="field">
            <label for="email" class="form-label">Email</label>
            <input type="email" class="form-control" id="email" formControlName="email" autocomplete="email"
                   [attr.aria-invalid]="!!error('email')" [attr.aria-describedby]="error('email') ? 'email-error' : null" />
            @if (error('email'); as message) {
              <p class="field-error" id="email-error">{{ message }}</p>
            }
          </div>

          <fieldset class="account-kind">
            <legend>How will you use Wovely?</legend>
            <label class="kind">
              <input type="radio" name="accountKind" formControlName="accountKind" value="buyer" />
              <span class="kind-text">
                <span class="kind-title">Shop</span>
                <span class="kind-hint">Buy from makers and follow your orders.</span>
              </span>
            </label>
            <label class="kind">
              <input type="radio" name="accountKind" formControlName="accountKind" value="seller" />
              <span class="kind-text">
                <span class="kind-title">Shop and sell my work</span>
                <span class="kind-hint">List what you make. A moderator checks each listing before buyers see it.</span>
              </span>
            </label>
          </fieldset>

          <div class="field">
            <label for="password" class="form-label">Password</label>
            <input type="password" class="form-control" id="password" formControlName="password" autocomplete="new-password"
                   [attr.aria-invalid]="!!error('password')" [attr.aria-describedby]="error('password') ? 'password-error' : null" />
            @if (error('password'); as message) {
              <p class="field-error" id="password-error">{{ message }}</p>
            }
          </div>

          @if (errorMessage(); as message) {
            <div class="alert alert-danger" role="alert">{{ message }}</div>
          }

          <button class="btn btn-primary" [disabled]="submitting()">Create account</button>
        </form>

        <p class="switch">Already have an account? <a routerLink="/login">Log in</a></p>
      }
    </div>
  `,
  styles: `
    .auth {
      max-width: 26rem;
      margin: var(--wv-space-7) auto var(--wv-space-8);
      padding-inline: var(--wv-gutter);
    }
    h1 { margin-bottom: var(--wv-space-2); }
    .lede { color: var(--wv-ink-soft); margin-bottom: var(--wv-space-5); }
    .field { margin-bottom: var(--wv-space-4); }
    .field-error { margin: var(--wv-space-1) 0 0; font-size: var(--wv-text-sm); color: var(--wv-danger); }
    .btn { width: 100%; }
    .account-kind { margin: 0 0 var(--wv-space-4); padding: 0; border: 0; }
    .account-kind legend { font-size: var(--wv-text-md); margin-bottom: var(--wv-space-2); }
    .kind {
      display: flex;
      gap: var(--wv-space-3);
      align-items: flex-start;
      padding: var(--wv-space-3) var(--wv-space-4);
      margin-bottom: var(--wv-space-2);
      background: var(--wv-surface-raised);
      border: 1px solid var(--wv-line);
      border-radius: var(--wv-radius-m);
      cursor: pointer;
    }
    /* the chosen option gets a stitched edge, like the verified badge */
    .kind:has(input:checked) { border: 1px dashed var(--wv-primary); box-shadow: inset 3px 0 0 var(--wv-primary); }
    .kind:has(input:focus-visible) { outline: 2px solid var(--wv-focus); outline-offset: 2px; }
    .kind input { margin-top: 0.3rem; accent-color: var(--wv-primary); }
    .kind-text { display: grid; gap: 0.1rem; }
    .kind-title { font-weight: 600; }
    .kind-hint { font-size: var(--wv-text-sm); color: var(--wv-ink-soft); }
    .switch { margin-top: var(--wv-space-5); color: var(--wv-ink-soft); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterComponent {
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);
  private fb = inject(FormBuilder);

  form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(20)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(50)]],
    password: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(40)]],
    accountKind: this.fb.nonNullable.control<'buyer' | 'seller'>('buyer'),
  });

  isSuccessful = signal(false);
  submitting = signal(false);
  errorMessage = signal<string | null>(null);
  returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');

  error(field: Field): string | null {
    const control = this.form.controls[field];
    if (control.valid || !(control.dirty || control.touched)) return null;
    if (control.hasError('required')) return `Enter your ${field}.`;
    if (control.hasError('email')) return 'Enter a valid email address.';
    if (control.hasError('minlength')) {
      return `Use at least ${control.getError('minlength').requiredLength} characters.`;
    }
    return `Use at most ${control.getError('maxlength').requiredLength} characters.`;
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, email, password, accountKind } = this.form.getRawValue();
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.authService.register(username, email, password, accountKind === 'seller').subscribe({
      next: () => this.isSuccessful.set(true),
      error: (err: unknown) => {
        this.errorMessage.set(apiErrorMessage(err, 'Your account wasn’t created. Try again.'));
        this.submitting.set(false);
      },
    });
  }
}
