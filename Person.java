package com.hananimed.model;

/**
 * Common base for every person stored by the clinic (patients and staff).
 * The ID is immutable because appointments reference people by ID.
 */
public abstract class Person {
    protected final String id;
    protected String name;
    protected String phone;

    protected Person(String id, String name, String phone) {
        this.id = id;
        this.name = name;
        this.phone = phone;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    /** One-line, human-readable summary used by the console UI. */
    public abstract String getDetails();
}
