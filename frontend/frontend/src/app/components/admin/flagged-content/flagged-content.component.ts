import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdminService } from '../../../services/admin.service';

@Component({
    selector: 'app-flagged-content',
    standalone: true,
    imports: [CommonModule],
    template: `
        <div class="flagged-content p-4">
            <h4 class="fw-bold mb-4">Safety Monitoring: Flagged Content</h4>
            <div class="row g-4">
                @for (report of reports(); track report.id) {
                    <div class="col-12">
                        <div class="card border-0 shadow-sm rounded-4 overflow-hidden">
                            <div class="card-header border-0 bg-light p-3 d-flex justify-content-between align-items-center">
                                <div>
                                    <span class="badge bg-danger me-2">{{ report.targetType }}</span>
                                    <span class="text-muted extra-small">Report #{{ report.id }}</span>
                                </div>
                                <span class="badge rounded-pill" [class.bg-warning]="report.status === 'PENDING'" [class.bg-success]="report.status === 'RESOLVED'">
                                    {{ report.status }}
                                </span>
                            </div>
                            <div class="card-body p-4">
                                <div class="row">
                                    <div class="col-md-8">
                                        <h6 class="fw-bold mb-1">Reason for Flag:</h6>
                                        <p class="text-dark">{{ report.reason }}</p>
                                        <div class="extra-small text-muted mb-3">
                                            <span class="fw-bold">Reporter:</span> {{ report.reporterName }} ({{ report.reporterId }})<br>
                                            <span class="fw-bold">Target:</span> {{ report.targetName }} ({{ report.targetId }})
                                        </div>
                                    </div>
                                    <div class="col-md-4 text-end">
                                        <div class="btn-group-vertical w-100 gap-2">
                                            @if (report.targetType === 'CHAT') {
                                                <button class="btn btn-outline-dark btn-sm rounded-pill" (click)="viewChatLog(report)">
                                                    <i class="bi bi-eye-fill me-1"></i> View Reported Log
                                                </button>
                                            }
                                            <button class="btn btn-success btn-sm rounded-pill" (click)="resolve(report.id, 'RESOLVED')">
                                                Resolve
                                            </button>
                                            <button class="btn btn-outline-secondary btn-sm rounded-pill" (click)="resolve(report.id, 'DISMISSED')">
                                                Dismiss
                                            </button>
                                        </div>
                                    </div>
                                </div>
                                
                                @if (activeChatLog() && selectedReportId() === report.id) {
                                    <div class="mt-4 p-3 bg-light rounded-4 border chat-preview animate-fade-in">
                                        <h6 class="fw-bold small mb-3">Reported Conversation Log</h6>
                                        <div class="log-container overflow-auto" style="max-height: 300px;">
                                            @for (msg of activeChatLog(); track msg.id) {
                                                <div class="mb-2 p-2 rounded-3 bg-white shadow-xs">
                                                    <span class="fw-bold extra-small">{{ resolveParticipantName(report, msg.senderId) }}:</span>
                                                    <span class="small ms-2">{{ msg.content }}</span>
                                                </div>
                                            }
                                        </div>
                                    </div>
                                }
                            </div>
                        </div>
                    </div>
                } @empty {
                    <div class="col-12 text-center py-5 text-muted">
                        <i class="bi bi-shield-check display-1 opacity-25"></i>
                        <p class="mt-3">The moderation queue is empty.</p>
                    </div>
                }
            </div>
        </div>
    `,
    styles: [`
        .extra-small { font-size: 0.7rem; font-family: monospace; }
        .shadow-xs { box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
        .animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
        @keyframes fadeIn { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }
    `]
})
export class FlaggedContentComponent implements OnInit {
    private adminService = inject(AdminService);
    public reports = signal<any[]>([]);
    public activeChatLog = signal<any[] | null>(null);
    public selectedReportId = signal<string | null>(null);

    ngOnInit(): void {
        this.loadReports();
    }

    loadReports(): void {
        this.adminService.getAllReports().subscribe(data => this.reports.set(data));
    }

    resolve(id: string, status: string): void {
        this.adminService.updateReportStatus(id, status).subscribe(() => this.loadReports());
    }

    viewChatLog(report: any): void {
        if (this.selectedReportId() === report.id) {
            this.selectedReportId.set(null);
            this.activeChatLog.set(null);
            return;
        }
        
        this.adminService.getReportedChatLog(report.id).subscribe(log => {
            this.activeChatLog.set(log);
            this.selectedReportId.set(report.id);
        });
    }

    resolveParticipantName(report: any, senderId: string): string {
        if (senderId === report.reporterId) {
            return report.reporterName || 'Reporter';
        }
        // If it's a chat report, the targetName is "Chat with TargetUser"
        if (report.targetType === 'CHAT' && report.targetName) {
            return report.targetName.replace('Chat with ', '');
        }
        return 'User #' + senderId.substring(0, 6);
    }
}
