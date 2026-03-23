import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../../services/admin.service';
import { UserCrm } from '../../../models/user-crm.model';
import { UserProfileComponent } from '../user-profile/user-profile';

@Component({
  selector: 'app-user-crm',
  standalone: true,
  imports: [CommonModule, FormsModule, UserProfileComponent],
  templateUrl: './user-crm.html',
  styleUrls: ['./user-crm.css']
})
export class UserCrmComponent implements OnInit {
  private adminService = inject(AdminService);

  users = signal<UserCrm[]>([]);
  selectedUser = signal<UserCrm | null>(null);
  searchQuery = signal('');
  statusFilter = signal<'ALL' | 'ACTIVE' | 'SUSPENDED' | 'BANNED'>('ALL');

  filteredUsers = computed(() => {
    const query = this.searchQuery().toLowerCase();
    const status = this.statusFilter();
    
    return this.users().filter(user => {
      const matchesSearch = user.username.toLowerCase().includes(query) || 
                           user.email.toLowerCase().includes(query);
      const matchesStatus = status === 'ALL' || user.accountStatus === status;
      return matchesSearch && matchesStatus;
    });
  });

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
    this.loadUsers();
  }

  resetFilters(): void {
    this.searchQuery.set('');
    this.statusFilter.set('ALL');
  }
}
