import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Category } from '../models/category.model';
import { environment } from '../../environments/environment';

const API_URL = environment.apiUrl + '/categories';

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
