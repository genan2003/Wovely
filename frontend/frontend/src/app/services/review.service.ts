import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Review, ReviewResponse } from '../models/review.model';

const API_URL = 'http://localhost:8082/api/reviews';

@Injectable({
    providedIn: 'root'
})
export class ReviewService {
    private http = inject(HttpClient);

    getReviewsByProductId(productId: string): Observable<ReviewResponse> {
        return this.http.get<ReviewResponse>(`${API_URL}/product/${productId}`);
    }

    createReview(review: Review): Observable<Review> {
        return this.http.post<Review>(API_URL, review);
    }
}
