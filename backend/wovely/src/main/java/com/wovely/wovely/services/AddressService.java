package com.wovely.wovely.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.wovely.common.error.ApiException;
import com.wovely.wovely.models.Address;
import com.wovely.wovely.models.User;
import com.wovely.wovely.payload.request.AddressRequest;
import com.wovely.wovely.repository.UserRepository;

/**
 * The caller's shipping address book. Invariant: when the book is non-empty exactly one entry is the
 * default (the first one added, or the last one marked default).
 */
@Service
public class AddressService {

    public static final int MAX_ADDRESSES = 10;

    private final UserRepository users;

    public AddressService(UserRepository users) {
        this.users = users;
    }

    public List<Address> list(String userId) {
        return load(userId).getAddresses();
    }

    public Address add(String userId, AddressRequest req) {
        User user = load(userId);
        List<Address> book = user.getAddresses();
        if (book.size() >= MAX_ADDRESSES) {
            throw ApiException.conflict("You can save at most " + MAX_ADDRESSES + " addresses");
        }
        Address a = new Address();
        a.setId(UUID.randomUUID().toString());
        apply(a, req);
        book.add(a);
        if (book.size() == 1 || Boolean.TRUE.equals(req.makeDefault())) {
            makeDefault(book, a.getId());
        }
        users.save(user);
        return a;
    }

    public Address update(String userId, String addressId, AddressRequest req) {
        User user = load(userId);
        Address a = find(user, addressId);
        apply(a, req);
        if (Boolean.TRUE.equals(req.makeDefault())) {
            makeDefault(user.getAddresses(), addressId);
        }
        users.save(user);
        return a;
    }

    public Address setDefault(String userId, String addressId) {
        User user = load(userId);
        Address a = find(user, addressId);
        makeDefault(user.getAddresses(), addressId);
        users.save(user);
        return a;
    }

    public void delete(String userId, String addressId) {
        User user = load(userId);
        Address a = find(user, addressId);
        List<Address> book = user.getAddresses();
        book.remove(a);
        if (a.isDefault() && !book.isEmpty()) {
            book.get(0).setDefault(true);
        }
        users.save(user);
    }

    /**
     * Address to ship an order to: the given entry of the caller's book, else free text (legacy clients),
     * else the default entry. 404 for an id that isn't in the caller's book, 400 when nothing is known.
     */
    public String resolveForCheckout(String userId, String addressId, String freeText) {
        User user = load(userId);
        if (addressId != null && !addressId.isBlank()) {
            return find(user, addressId).format();
        }
        if (freeText != null && !freeText.isBlank()) {
            return freeText.trim();
        }
        return user.getAddresses().stream().filter(Address::isDefault).findFirst()
                .map(Address::format)
                .orElseThrow(() -> ApiException.badRequest("Add a shipping address before checking out"));
    }

    private User load(String userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private static Address find(User user, String addressId) {
        Optional<Address> a = user.getAddresses().stream().filter(x -> x.getId().equals(addressId)).findFirst();
        // Same answer for "not yours" and "doesn't exist": ids aren't guessable but don't leak either.
        return a.orElseThrow(() -> ApiException.notFound("Address not found"));
    }

    private static void makeDefault(List<Address> book, String addressId) {
        book.forEach(x -> x.setDefault(x.getId().equals(addressId)));
    }

    private static void apply(Address a, AddressRequest r) {
        a.setLabel(blankToNull(r.label()));
        a.setRecipientName(r.recipientName().trim());
        a.setStreet(r.street().trim());
        a.setCity(r.city().trim());
        a.setPostalCode(blankToNull(r.postalCode()));
        a.setCountry(r.country().trim());
        a.setPhone(blankToNull(r.phone()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
