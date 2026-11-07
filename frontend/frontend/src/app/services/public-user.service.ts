import { SellerReview } from '../models/seller-review.model';
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SellerProfile } from '../models/seller-profile.model';
import { environment } from '../../environments/environment';

const API_URL = environment.apiUrl + '/public/users';

@Injectable({
    providedIn: 'root'
})
export class PublicUserService {
    private http = inject(HttpClient);

    getSellerProfile(id: string): Observable<SellerProfile> {
        return this.http.get<SellerProfile>(`${API_URL}/seller/${id}`);
    }

    getSellerReviews(id: string): Observable<SellerReview[]> {
        return this.http.get<SellerReview[]>(`${API_URL}/seller/${id}/reviews`);
    }

    postSellerReview(id: string, review: Partial<SellerReview>): Observable<SellerReview> {
        return this.http.post<SellerReview>(`${API_URL}/seller/${id}/reviews`, review);
    }
}
