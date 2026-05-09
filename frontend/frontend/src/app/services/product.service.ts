import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Product } from '../models/product.model';

const API_URL = 'http://localhost:8082/api/products';

@Injectable({
    providedIn: 'root'
})
export class ProductService {
    private http = inject(HttpClient);

    constructor() { }

    getAllProducts(filters?: any): Observable<Product[]> {
        let url = API_URL;
        if (filters) {
            const params = new URLSearchParams();
            Object.keys(filters).forEach(key => {
                const value = filters[key];
                if (value !== null && value !== undefined && value !== '') {
                    if (Array.isArray(value)) {
                        value.forEach(v => params.append(key, v));
                    } else {
                        params.append(key, value);
                    }
                }
            });
            const queryString = params.toString();
            if (queryString) {
                url += `?${queryString}`;
            }
        }
        return this.http.get<Product[]>(url);
    }

    getProductsByCategory(category: string): Observable<Product[]> {
        return this.http.get<Product[]>(`${API_URL}?category=${category}`);
    }

    getProductById(id: string): Observable<Product> {
        return this.http.get<Product>(`${API_URL}/${id}`);
    }

    createProduct(product: Product): Observable<Product> {
        return this.http.post<Product>(API_URL, product);
    }
}
