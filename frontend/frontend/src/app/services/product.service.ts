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

    getAllProducts(): Observable<Product[]> {
        return this.http.get<Product[]>(API_URL);
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
