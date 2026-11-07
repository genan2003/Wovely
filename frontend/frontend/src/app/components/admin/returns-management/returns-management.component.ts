import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ReturnService, ReturnRequest } from '../../../services/return.service';
import { ToastService } from '../../../services/toast.service';

@Component({
  selector: 'app-admin-returns',
  imports: [DecimalPipe, DatePipe],
  templateUrl: './returns-management.component.html',
  styleUrl: './returns-management.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminReturnsComponent implements OnInit {
  public returnService = inject(ReturnService);
  private toast = inject(ToastService);
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
      error: (err: unknown) => {
        this.toast.apiError(err, 'Returns couldn’t be loaded.');
        this.loading.set(false);
      },
    });
  }

  updateStatus(id: string, status: string): void {
    this.returnService.updateStatus(id, status).subscribe({
      next: () => this.loadAllReturns(),
      error: (err: unknown) => this.toast.apiError(err, 'The return wasn’t updated. Try again.'),
    });
  }
}
