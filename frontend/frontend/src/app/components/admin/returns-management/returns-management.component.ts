import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReturnService, ReturnRequest } from '../../../services/return.service';

@Component({
    selector: 'app-admin-returns',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './returns-management.component.html',
    styleUrl: './returns-management.component.css'
})
export class AdminReturnsComponent implements OnInit {
    public returnService = inject(ReturnService);
    public returns = signal<ReturnRequest[]>([]);
    public loading = signal<boolean>(true);

    ngOnInit(): void {
        this.loadAllReturns();
    }

    loadAllReturns(): void {
        this.loading.set(true);
        this.returnService.loadAllReturns().subscribe({
            next: (data) => {
                this.returns.set(data);
                this.loading.set(false);
            },
            error: () => this.loading.set(false)
        });
    }

    updateStatus(id: string, status: string): void {
        this.returnService.updateStatus(id, status).subscribe(() => {
            this.loadAllReturns();
        });
    }
}
