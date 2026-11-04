import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Address, AddressRequest } from '../models/address.model';
import { environment } from '../../environments/environment';

const API_URL = environment.apiUrl + '/user/addresses';

/** The signed-in user's own address book. */
@Injectable({ providedIn: 'root' })
export class AddressService {
  private http = inject(HttpClient);

  list(): Observable<Address[]> {
    return this.http.get<Address[]>(API_URL);
  }

  add(address: AddressRequest): Observable<Address> {
    return this.http.post<Address>(API_URL, address);
  }

  update(id: string, address: AddressRequest): Observable<Address> {
    return this.http.put<Address>(`${API_URL}/${encodeURIComponent(id)}`, address);
  }

  makeDefault(id: string): Observable<Address> {
    return this.http.post<Address>(`${API_URL}/${encodeURIComponent(id)}/default`, {});
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${API_URL}/${encodeURIComponent(id)}`);
  }
}
