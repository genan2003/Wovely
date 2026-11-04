package com.wovely.wovely.models;

/** One entry of a user's address book, embedded in {@link User} (PLAN.md 3 — address at checkout). */
public class Address {
    private String id;
    private String label;
    private String recipientName;
    private String street;
    private String city;
    private String postalCode;
    private String country;
    private String phone;
    private boolean isDefault;

    public Address() {
    }

    /** Single-line form stored on orders and printed on shipping labels. */
    public String format() {
        StringBuilder sb = new StringBuilder();
        append(sb, recipientName);
        append(sb, street);
        append(sb, (postalCode == null ? "" : postalCode + " ") + (city == null ? "" : city));
        append(sb, country);
        if (phone != null && !phone.isBlank()) {
            append(sb, "tel. " + phone);
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(part.trim());
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
}
