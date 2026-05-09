import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdminService } from '../../../services/admin.service';

@Component({
    selector: 'app-audit-trail',
    standalone: true,
    imports: [CommonModule],
    template: `
        <div class="audit-trail p-4">
            <h4 class="fw-bold mb-4">Admin Intervention Audit Trail</h4>
            <div class="table-responsive bg-white rounded-4 shadow-sm">
                <table class="table table-hover align-middle mb-0">
                    <thead class="table-light">
                        <tr>
                            <th>Timestamp</th>
                            <th>Admin</th>
                            <th>Action</th>
                            <th>Target ID</th>
                            <th>Details</th>
                        </tr>
                    </thead>
                    <tbody>
                        @for (log of logs(); track log.id) {
                            <tr>
                                <td class="small">{{ log.timestamp | date:'short' }}</td>
                                <td><span class="badge bg-secondary">{{ log.adminUsername }}</span></td>
                                <td><span class="fw-bold small">{{ log.actionType }}</span></td>
                                <td class="extra-small">{{ log.targetId }}</td>
                                <td class="small italic">{{ log.details }}</td>
                            </tr>
                        } @empty {
                            <tr>
                                <td colspan="5" class="text-center py-5 text-muted">No intervention logs found.</td>
                            </tr>
                        }
                    </tbody>
                </table>
            </div>
        </div>
    `,
    styles: [`
        .extra-small { font-size: 0.7rem; font-family: monospace; }
        .italic { font-style: italic; }
    `]
})
export class AuditTrailComponent implements OnInit {
    private adminService = inject(AdminService);
    public logs = signal<any[]>([]);

    ngOnInit(): void {
        this.adminService.getAuditLogs().subscribe(data => this.logs.set(data));
    }
}
