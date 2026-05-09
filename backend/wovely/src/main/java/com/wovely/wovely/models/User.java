package com.wovely.wovely.models;

import java.util.HashSet;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Document(collection = "users")
public class User {
    @Id
    private String id;

    @NotBlank
    @Size(max = 20)
    private String username;

    @NotBlank
    @Size(max = 50)
    @Email
    private String email;

    @NotBlank
    @Size(max = 120)
    private String password;

    @DBRef
    private Set<Role> roles = new HashSet<>();

    private int strikes = 0;
    
    private EAccountStatus accountStatus = EAccountStatus.ACTIVE;
    
    private java.util.Date suspendedUntil;

    private String fullName;

    // Seller profile fields
    private String makerStory;
    private String workshopImageUrl;
    private String city;
    private String region;

    public User() {
    }

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.strikes = 0;
        this.accountStatus = EAccountStatus.ACTIVE;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public int getStrikes() {
        return strikes;
    }

    public void setStrikes(int strikes) {
        this.strikes = strikes;
    }

    public EAccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(EAccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public java.util.Date getSuspendedUntil() {
        return suspendedUntil;
    }

    public void setSuspendedUntil(java.util.Date suspendedUntil) {
        this.suspendedUntil = suspendedUntil;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getMakerStory() {
        return makerStory;
    }

    public void setMakerStory(String makerStory) {
        this.makerStory = makerStory;
    }

    public String getWorkshopImageUrl() {
        return workshopImageUrl;
    }

    public void setWorkshopImageUrl(String workshopImageUrl) {
        this.workshopImageUrl = workshopImageUrl;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
