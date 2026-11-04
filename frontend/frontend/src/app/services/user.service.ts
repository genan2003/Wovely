import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

const API_URL = environment.apiUrl + '/user';

@Injectable({
    providedIn: 'root'
})
export class UserService {
    private http = inject(HttpClient);

    getUserProfile(id: string): Observable<any> {
        return this.http.get<any>(`${API_URL}/profile/${id}`);
    }

    updateProfile(id: string, data: any): Observable<any> {
        return this.http.put<any>(`${API_URL}/profile/${id}`, data);
    }
}
