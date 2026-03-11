package com.wovely.wovely.payload.response;

import java.util.List;
import java.util.Date;
import com.wovely.wovely.models.EAccountStatus;

public class UserCrmDTO {
    private String id;
    private String username;
    private String email;
    private List<String> roles;
    private int strikes;
    private EAccountStatus accountStatus;
    private Date suspendedUntil;

    public UserCrmDTO(String id, String username, String email, List<String> roles, int strikes, EAccountStatus accountStatus, Date suspendedUntil) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.roles = roles;
        this.strikes = strikes;
        this.accountStatus = accountStatus;
        this.suspendedUntil = suspendedUntil;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public int getStrikes() { return strikes; }
    public void setStrikes(int strikes) { this.strikes = strikes; }

    public EAccountStatus getAccountStatus() { return accountStatus; }
    public void setAccountStatus(EAccountStatus accountStatus) { this.accountStatus = accountStatus; }

    public Date getSuspendedUntil() { return suspendedUntil; }
    public void setSuspendedUntil(Date suspendedUntil) { this.suspendedUntil = suspendedUntil; }
}
