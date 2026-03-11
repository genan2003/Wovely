import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdminService } from '../../../services/admin.service';
import { UserCrm } from '../../../models/user-crm.model';
import { UserProfileComponent } from '../user-profile/user-profile';

@Component({
  selector: 'app-user-crm',
  standalone: true,
  imports: [CommonModule, UserProfileComponent],
  templateUrl: './user-crm.html',
  styleUrls: ['./user-crm.css']
})
export class UserCrmComponent implements OnInit {
  private adminService = inject(AdminService);

  users = signal<UserCrm[]>([]);
  selectedUser = signal<UserCrm | null>(null);

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.adminService.getAllUsers().subscribe({
      next: (data) => this.users.set(data || []),
      error: (err) => console.error('Failed to load users', err)
    });
  }

  viewProfile(user: UserCrm): void {
    this.selectedUser.set(user);
  }

  closeProfile(): void {
    this.selectedUser.set(null);
    this.loadUsers(); // refresh data in case penalties were applied
  }
}
