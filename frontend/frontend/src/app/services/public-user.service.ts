import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SellerProfile } from '../models/seller-profile.model';

const API_URL = 'http://localhost:8081/api/public/users';

@Injectable({
    providedIn: 'root'
})
export class PublicUserService {
    private http = inject(HttpClient);

    getSellerProfile(id: string): Observable<SellerProfile> {
        return this.http.get<SellerProfile>(`${API_URL}/seller/${id}`);
    }

    getSellerReviews(id: string): Observable<any[]> {
        return this.http.get<any[]>(`${API_URL}/seller/${id}/reviews`);
    }

    postSellerReview(id: string, review: any): Observable<any> {
        return this.http.post<any>(`${API_URL}/seller/${id}/reviews`, review);
    }
}
