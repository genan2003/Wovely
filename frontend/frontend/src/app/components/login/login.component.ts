import { Component, ChangeDetectionStrategy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="col-md-12">
      <div class="card card-container auth-form-container">
        <!-- Optional Wovely branding image here -->
        <h2 class="text-center auth-title">Login</h2>
        
        <form
          [formGroup]="form"
          (ngSubmit)="onSubmit()"
          class="auth-form"
          aria-labelledby="login-form"
        >
          <div class="form-group mb-3">
            <label for="username" class="form-label">Username</label>
            <input
              type="text"
              class="form-control"
              id="username"
              formControlName="username"
              aria-describedby="usernameHelp"
            />
            @if (form.controls['username'].invalid && (form.controls['username'].dirty || form.controls['username'].touched)) {
              <div class="text-danger mt-1 validation-error" role="alert">
                Username is required.
              </div>
            }
          </div>

          <div class="form-group mb-3">
            <label for="password" class="form-label">Password</label>
            <input
              type="password"
              class="form-control"
              id="password"
              formControlName="password"
            />
            @if (form.controls['password'].invalid && (form.controls['password'].dirty || form.controls['password'].touched)) {
              <div class="text-danger mt-1 validation-error" role="alert">
                Password is required.
              </div>
            }
          </div>

          <div class="form-group mb-3 d-grid">
            <button class="btn btn-primary btn-block" [disabled]="form.invalid || isLoginFailed()">
              Login
            </button>
          </div>

          @if (isLoginFailed()) {
            <div class="form-group mb-3">
              <div class="alert alert-danger" role="alert">
                Login failed: {{ errorMessage() }}
              </div>
            </div>
          }
        </form>
      </div>
    </div>
  `,
  styles: [`
    .auth-form-container {
      max-width: 400px;
      margin: 2rem auto;
      padding: 2rem;
      border: 1px solid #ddd;
      border-radius: 8px;
      box-shadow: 0 4px 6px rgba(0,0,0,0.1);
    }
    .auth-title {
      margin-bottom: 1.5rem;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoginComponent {
  private authService = inject(AuthService);
  private router = inject(Router);
  private fb = inject(FormBuilder);

  form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required]
  });

  isLoginFailed = signal(false);
  errorMessage = signal('');

  onSubmit(): void {
    if (this.form.invalid) return;

    const { username, password } = this.form.getRawValue();

    this.authService.login(username, password).subscribe({
      next: () => {
        this.isLoginFailed.set(false);
        this.router.navigate(['/products']);
      },
      error: err => {
        this.errorMessage.set(err.error?.message || 'Unknown error occurred');
        this.isLoginFailed.set(true);
      }
    });
  }
}
