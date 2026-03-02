import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { StorageService } from './storage.service';

const AUTH_API = 'http://localhost:8081/api/auth/';

const httpOptions = {
    headers: new HttpHeaders({ 'Content-Type': 'application/json' })
};

@Injectable({
    providedIn: 'root',
})
export class AuthService {
    private http = inject(HttpClient);
    private storageService = inject(StorageService);

    // Global authentication state using signal
    public isLoggedIn = signal<boolean>(this.storageService.isLoggedIn());

    login(username: string, password: string): Observable<any> {
        return this.http.post(AUTH_API + 'signin', { username, password }, httpOptions).pipe(
            tap((data: any) => {
                if (data.accessToken) {
                    this.storageService.saveToken(data.accessToken);
                    this.storageService.saveUser(data);
                    this.isLoggedIn.set(true);
                }
            })
        );
    }

    register(username: string, email: string, password: string): Observable<any> {
        return this.http.post(AUTH_API + 'signup', { username, email, password }, httpOptions);
    }

    logout(): Observable<any> {
        // We could call a backend logout endpoint here if we made one.
        // For now we just clean the local storage
        return new Observable(observer => {
            this.storageService.clean();
            this.isLoggedIn.set(false);
            observer.next(null);
            observer.complete();
        });
    }
}
