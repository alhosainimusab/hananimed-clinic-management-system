package com.hananimed.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A registered clinic patient with a running medical history. */
public class Patient extends Person {
    private int age;
    private String gender;
    private final List<String> medicalHistory = new ArrayList<>();

    public Patient(String id, String name, String phone, int age, String gender) {
        super(id, name, phone);
        this.age = age;
        this.gender = gender;
    }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    /** Read-only view; use {@link #addMedicalHistory(String)} to add entries. */
    public List<String> getMedicalHistory() {
        return Collections.unmodifiableList(medicalHistory);
    }

    public void addMedicalHistory(String note) {
        if (note != null && !note.isBlank()) {
            medicalHistory.add(note.trim());
        }
    }

    @Override
    public String getDetails() {
        return String.format("Patient[ID=%s, Name=%s, Phone=%s, Age=%d, Gender=%s]",
                id, name, phone, age, gender);
    }
}
