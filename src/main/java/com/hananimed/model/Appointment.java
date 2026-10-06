package com.hananimed.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** A booking that links one patient to one doctor at a specific date and time. */
public class Appointment {
    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String apptId;
    private String patientId;
    private String doctorId;
    private LocalDateTime dateTime;
    private String notes;

    public Appointment(String apptId, String patientId, String doctorId, LocalDateTime dateTime, String notes) {
        this.apptId = apptId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.dateTime = dateTime;
        this.notes = notes == null ? "" : notes;
    }

    public String getApptId() { return apptId; }
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public String getDoctorId() { return doctorId; }
    public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
    public LocalDateTime getDateTime() { return dateTime; }
    public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes == null ? "" : notes; }

    public String getDetails() {
        return String.format("Appt[ID=%s, Patient=%s, Doctor=%s, Date=%s, Notes=%s]",
                apptId, patientId, doctorId, dateTime.format(FORMAT), notes);
    }
}
