import { Component, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from './services/auth.service';
import { StorageService } from './services/storage.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private authService = inject(AuthService);
  private storageService = inject(StorageService);

  // Use computed or the signal directly from authService
  public isLoggedIn = this.authService.isLoggedIn;

  isAdmin(): boolean {
    const user = this.storageService.getUser();
    return user && user.roles && user.roles.includes('ROLE_ADMIN');
  }

  logout(event: Event): void {
    event.preventDefault();
    this.authService.logout().subscribe();
  }
}
