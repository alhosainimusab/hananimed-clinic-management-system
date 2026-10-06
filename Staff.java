package com.hananimed.model;

/** A clinic employee. Only staff with the Doctor role can log in to the doctor menu. */
public class Staff extends Person {
    public static final String ROLE_DOCTOR = "Doctor";
    public static final String ROLE_RECEPTIONIST = "Receptionist";

    private String role;
    private String email;

    public Staff(String id, String name, String phone, String role, String email) {
        super(id, name, phone);
        this.role = role;
        this.email = email;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isDoctor() { return ROLE_DOCTOR.equalsIgnoreCase(role); }

    @Override
    public String getDetails() {
        return String.format("Staff[ID=%s, Name=%s, Phone=%s, Role=%s, Email=%s]",
                id, name, phone, role, email);
    }
}
