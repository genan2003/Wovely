import { Component, inject, ChangeDetectionStrategy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StorageService } from '../../services/storage.service';

@Component({
    selector: 'app-profile',
    standalone: true,
    imports: [CommonModule],
    template: `
    <div class="container mt-4" *ngIf="currentUser()">
      <header class="jumbotron">
        <h3>
          <strong>{{ currentUser().username }}</strong> Profile
        </h3>
      </header>
      <p>
        <strong>Token:</strong>
        {{ currentUser().accessToken.substring(0, 20) }} ... {{ currentUser().accessToken.substring(currentUser().accessToken.length - 20) }}
      </p>
      <p>
        <strong>Email:</strong>
        {{ currentUser().email }}
      </p>
      <strong>Roles:</strong>
      <ul>
        <li *ngFor="let role of currentUser().roles">
          {{ role }}
        </li>
      </ul>
    </div>
  `,
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProfileComponent implements OnInit {
    private storageService = inject(StorageService);
    currentUser = signal<any>(null);

    ngOnInit(): void {
        this.currentUser.set(this.storageService.getUser());
    }
}
