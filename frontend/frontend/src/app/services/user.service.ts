import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { UserProfile } from '../models/user-profile.model';

const API_URL = environment.apiUrl + '/user';

@Injectable({
    providedIn: 'root'
})
export class UserService {
    private http = inject(HttpClient);

    getUserProfile(id: string): Observable<UserProfile> {
        return this.http.get<UserProfile>(`${API_URL}/profile/${id}`);
    }

    updateProfile(id: string, data: UserProfile): Observable<UserProfile> {
        return this.http.put<UserProfile>(`${API_URL}/profile/${id}`, data);
    }
}
