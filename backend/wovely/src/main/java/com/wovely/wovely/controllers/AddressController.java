package com.wovely.wovely.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.wovely.models.Address;
import com.wovely.wovely.payload.request.AddressRequest;
import com.wovely.wovely.security.services.UserDetailsImpl;
import com.wovely.wovely.services.AddressService;

import jakarta.validation.Valid;

/** The caller's own address book — there is no user id in the path, so no IDOR surface. */
@RestController
@RequestMapping("/api/user/addresses")
@PreAuthorize("isAuthenticated()")
public class AddressController {

    private final AddressService addresses;

    public AddressController(AddressService addresses) {
        this.addresses = addresses;
    }

    @GetMapping
    public List<Address> list(@AuthenticationPrincipal UserDetailsImpl me) {
        return addresses.list(me.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Address add(@Valid @RequestBody AddressRequest request, @AuthenticationPrincipal UserDetailsImpl me) {
        return addresses.add(me.getId(), request);
    }

    @PutMapping("/{addressId}")
    public Address update(@PathVariable String addressId, @Valid @RequestBody AddressRequest request,
            @AuthenticationPrincipal UserDetailsImpl me) {
        return addresses.update(me.getId(), addressId, request);
    }

    @PostMapping("/{addressId}/default")
    public Address makeDefault(@PathVariable String addressId, @AuthenticationPrincipal UserDetailsImpl me) {
        return addresses.setDefault(me.getId(), addressId);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String addressId, @AuthenticationPrincipal UserDetailsImpl me) {
        addresses.delete(me.getId(), addressId);
    }
}
