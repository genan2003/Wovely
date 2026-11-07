import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Product } from '../models/product.model';
import { environment } from '../../environments/environment';

const API_URL = environment.apiUrl + '/products';

@Injectable({
    providedIn: 'root'
})
export class ProductService {
    private http = inject(HttpClient);

    /** Empty filter values are left out of the query. */
    getAllProducts(filters?: object): Observable<Product[]> {
        const params: Record<string, string | number | boolean> = {};
        Object.entries(filters ?? {}).forEach(([key, value]) => {
            if (value !== null && value !== undefined && value !== '' && value !== false) {
                params[key] = value;
            }
        });
        return this.http.get<Product[]>(API_URL, { params });
    }

    getProductsByCategory(category: string): Observable<Product[]> {
        return this.http.get<Product[]>(API_URL, { params: { category } });
    }

    getProductById(id: string): Observable<Product> {
        return this.http.get<Product>(`${API_URL}/${id}`);
    }

    createProduct(product: Product): Observable<Product> {
        return this.http.post<Product>(API_URL, product);
    }
}
