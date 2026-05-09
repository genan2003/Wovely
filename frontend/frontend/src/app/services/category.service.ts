import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Category } from '../models/category.model';

const API_URL = 'http://localhost:8082/api/categories';

@Injectable({
    providedIn: 'root'
})
export class CategoryService {
    private http = inject(HttpClient);

    getRootCategories(): Observable<Category[]> {
        return this.http.get<Category[]>(`${API_URL}/roots`);
    }

    getSubCategories(parentId: string): Observable<Category[]> {
        return this.http.get<Category[]>(`${API_URL}/children/${parentId}`);
    }

    getAllCategories(): Observable<Category[]> {
        return this.http.get<Category[]>(API_URL);
    }
}
