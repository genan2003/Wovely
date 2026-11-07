import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { apiErrorMessage } from '../../services/toast.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth">
      <h1>Log in</h1>
      <p class="lede">Welcome back.</p>

      <form [formGroup]="form" (ngSubmit)="onSubmit()">
        <div class="field">
          <label for="username" class="form-label">Username</label>
          <input type="text" class="form-control" id="username" formControlName="username"
                 autocomplete="username" [attr.aria-invalid]="showError('username')"
                 [attr.aria-describedby]="showError('username') ? 'username-error' : null" />
          @if (showError('username')) {
            <p class="field-error" id="username-error">Enter your username.</p>
          }
        </div>

        <div class="field">
          <label for="password" class="form-label">Password</label>
          <input type="password" class="form-control" id="password" formControlName="password"
                 autocomplete="current-password" [attr.aria-invalid]="showError('password')"
                 [attr.aria-describedby]="showError('password') ? 'password-error' : null" />
          @if (showError('password')) {
            <p class="field-error" id="password-error">Enter your password.</p>
          }
        </div>

        @if (errorMessage(); as message) {
          <div class="alert alert-danger" role="alert">{{ message }}</div>
        }

        <button class="btn btn-primary" [disabled]="submitting()">Log in</button>
      </form>

      <p class="switch">New to Wovely? <a routerLink="/register" [queryParams]="{ returnUrl: returnUrl() }">Create an account</a></p>
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
    .switch { margin-top: var(--wv-space-5); color: var(--wv-ink-soft); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private fb = inject(FormBuilder);

  form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  errorMessage = signal<string | null>(null);
  submitting = signal(false);

  /** Where the guard interrupted the user. Only same-site paths are followed. */
  returnUrl(): string | null {
    const url = this.route.snapshot.queryParamMap.get('returnUrl');
    return url?.startsWith('/') && !url.startsWith('//') ? url : null;
  }

  showError(field: 'username' | 'password'): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.dirty || control.touched);
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.authService.login(username, password).subscribe({
      next: () => this.router.navigateByUrl(this.returnUrl() ?? '/products'),
      error: (err: HttpErrorResponse) => {
        const fallback =
          err.status === 401 ? 'That username and password don’t match.' : 'You couldn’t be logged in. Try again.';
        this.errorMessage.set(apiErrorMessage(err, fallback));
        this.submitting.set(false);
      },
    });
  }
}
