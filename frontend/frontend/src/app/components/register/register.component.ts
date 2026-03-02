import { Component, ChangeDetectionStrategy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { AuthService } from '../../services/auth.service';

@Component({
    selector: 'app-register',
    standalone: true,
    imports: [CommonModule, ReactiveFormsModule],
    template: `
    <div class="col-md-12">
      <div class="card card-container auth-form-container">
        <h2 class="text-center auth-title">Register</h2>
        
        <form
          [formGroup]="form"
          (ngSubmit)="onSubmit()"
          class="auth-form"
          aria-labelledby="register-form"
        >
          @if (!isSuccessful()) {
            <div class="form-group mb-3">
              <label for="username" class="form-label">Username</label>
              <input
                type="text"
                class="form-control"
                id="username"
                formControlName="username"
              />
              @if (form.controls['username'].invalid && (form.controls['username'].dirty || form.controls['username'].touched)) {
                <div class="text-danger mt-1 validation-error" role="alert">
                   @if (form.controls['username'].errors?.['required']) { <span>Username is required</span> }
                   @if (form.controls['username'].errors?.['minlength']) { <span>Username must be at least 3 characters</span> }
                   @if (form.controls['username'].errors?.['maxlength']) { <span>Username must be at most 20 characters</span> }
                </div>
              }
            </div>

            <div class="form-group mb-3">
              <label for="email" class="form-label">Email</label>
              <input
                type="email"
                class="form-control"
                id="email"
                formControlName="email"
              />
              @if (form.controls['email'].invalid && (form.controls['email'].dirty || form.controls['email'].touched)) {
                <div class="text-danger mt-1 validation-error" role="alert">
                   @if (form.controls['email'].errors?.['required']) { <span>Email is required</span> }
                   @if (form.controls['email'].errors?.['email']) { <span>Email must be a valid email address</span> }
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
                   @if (form.controls['password'].errors?.['required']) { <span>Password is required</span> }
                   @if (form.controls['password'].errors?.['minlength']) { <span>Password must be at least 6 characters</span> }
                </div>
              }
            </div>

            <div class="form-group mb-3 d-grid">
              <button class="btn btn-primary btn-block" [disabled]="form.invalid">
                Sign Up
              </button>
            </div>
          }

          @if (isSuccessful()) {
            <div class="alert alert-success" role="alert">
              Your registration is successful!
            </div>
          }

          @if (isSignUpFailed()) {
            <div class="alert alert-danger" role="alert">
              Signup failed!<br />{{ errorMessage() }}
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
export class RegisterComponent {
    private authService = inject(AuthService);
    private fb = inject(FormBuilder);

    form = this.fb.nonNullable.group({
        username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(20)]],
        email: ['', [Validators.required, Validators.email, Validators.maxLength(50)]],
        password: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(40)]]
    });

    isSuccessful = signal(false);
    isSignUpFailed = signal(false);
    errorMessage = signal('');

    onSubmit(): void {
        if (this.form.invalid) return;

        const { username, email, password } = this.form.getRawValue();

        this.authService.register(username, email, password).subscribe({
            next: data => {
                console.log(data);
                this.isSuccessful.set(true);
                this.isSignUpFailed.set(false);
            },
            error: err => {
                this.errorMessage.set(err.error?.message || 'Registration failed');
                this.isSignUpFailed.set(true);
            }
        });
    }
}
