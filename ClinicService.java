package com.hananimed.service;

import com.hananimed.model.Appointment;
import com.hananimed.model.Medicine;
import com.hananimed.model.Patient;
import com.hananimed.model.Staff;
import com.hananimed.persistence.ClinicDataManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * All clinic business rules live here, separate from the console UI, so they
 * can be unit-tested. Every successful change is saved to disk immediately.
 */
public class ClinicService {
    /** Length of one appointment slot; a doctor cannot have two bookings closer than this. */
    public static final int SLOT_MINUTES = 30;
    /** Medicines below this quantity appear in the low-stock report. */
    public static final int LOW_STOCK_THRESHOLD = 50;

    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Z]{1,3}\\d{1,6}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d{9,13}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s,]+@[^@\\s,]+\\.[^@\\s,]+$");

    private final ClinicDataManager store;
    private final Clock clock;
    private final List<Patient> patients;
    private final List<Staff> staff;
    private final List<Appointment> appointments;
    private final List<Medicine> inventory;

    public ClinicService(ClinicDataManager store, Clock clock) throws IOException {
        this.store = store;
        this.clock = clock;
        this.patients = new ArrayList<>(store.loadPatients());
        this.staff = new ArrayList<>(store.loadStaff());
        this.appointments = new ArrayList<>(store.loadAppointments());
        this.inventory = new ArrayList<>(store.loadMedicine());
    }

    public List<String> getLoadWarnings() { return store.getWarnings(); }

    // ======================= LOOKUPS =======================

    public List<Patient> getPatients() { return Collections.unmodifiableList(patients); }
    public List<Staff> getStaff() { return Collections.unmodifiableList(staff); }
    public List<Appointment> getAppointments() { return Collections.unmodifiableList(appointments); }
    public List<Medicine> getInventory() { return Collections.unmodifiableList(inventory); }

    public List<Staff> getDoctors() {
        return staff.stream().filter(Staff::isDoctor).collect(Collectors.toList());
    }

    public Optional<Patient> findPatient(String id) { return find(patients, Patient::getId, id); }
    public Optional<Staff> findStaff(String id) { return find(staff, Staff::getId, id); }
    public Optional<Appointment> findAppointment(String id) { return find(appointments, Appointment::getApptId, id); }
    public Optional<Medicine> findMedicine(String id) { return find(inventory, Medicine::getId, id); }

    public Optional<Staff> findDoctor(String id) { return findStaff(id).filter(Staff::isDoctor); }

    private static <T> Optional<T> find(List<T> list, Function<T, String> idOf, String id) {
        if (id == null) return Optional.empty();
        String key = id.trim();
        return list.stream().filter(x -> idOf.apply(x).equalsIgnoreCase(key)).findFirst();
    }

    // ======================= PATIENTS =======================

    public Patient registerPatient(String id, String name, String phone, int age, String gender) {
        String pid = validateNewId(id, findPatient(id).isPresent(), "Patient");
        Patient p = new Patient(pid, requireText(name, "Name"), validatePhone(phone),
                validateAge(age), validateGender(gender));
        patients.add(p);
        persist();
        return p;
    }

    /** Removes a patient and every appointment they had. Returns the number of appointments removed. */
    public int removePatient(String id) {
        Patient p = requirePatient(id);
        int before = appointments.size();
        appointments.removeIf(a -> a.getPatientId().equalsIgnoreCase(p.getId()));
        patients.remove(p);
        persist();
        return before - appointments.size();
    }

    public void updatePatientName(String id, String name) { requirePatient(id).setName(requireText(name, "Name")); persist(); }
    public void updatePatientPhone(String id, String phone) { requirePatient(id).setPhone(validatePhone(phone)); persist(); }
    public void updatePatientAge(String id, int age) { requirePatient(id).setAge(validateAge(age)); persist(); }
    public void updatePatientGender(String id, String gender) { requirePatient(id).setGender(validateGender(gender)); persist(); }

    // ======================= STAFF =======================

    public Staff addStaff(String id, String name, String phone, String role, String email) {
        String sid = validateNewId(id, findStaff(id).isPresent(), "Staff");
        Staff s = new Staff(sid, requireText(name, "Name"), validatePhone(phone),
                validateRole(role), validateEmail(email));
        staff.add(s);
        persist();
        return s;
    }

    /** Removes a staff member and every appointment assigned to them. Returns appointments removed. */
    public int removeStaff(String id) {
        Staff s = requireStaff(id);
        int before = appointments.size();
        appointments.removeIf(a -> a.getDoctorId().equalsIgnoreCase(s.getId()));
        staff.remove(s);
        persist();
        return before - appointments.size();
    }

    public void updateStaffName(String id, String name) { requireStaff(id).setName(requireText(name, "Name")); persist(); }
    public void updateStaffPhone(String id, String phone) { requireStaff(id).setPhone(validatePhone(phone)); persist(); }
    public void updateStaffEmail(String id, String email) { requireStaff(id).setEmail(validateEmail(email)); persist(); }

    public void updateStaffRole(String id, String role) {
        Staff s = requireStaff(id);
        String newRole = validateRole(role);
        if (s.isDoctor() && !Staff.ROLE_DOCTOR.equals(newRole)
                && appointments.stream().anyMatch(a -> a.getDoctorId().equalsIgnoreCase(s.getId()))) {
            throw new ClinicException("Cannot change role: " + s.getName() + " still has appointments.");
        }
        s.setRole(newRole);
        persist();
    }

    // ======================= APPOINTMENTS =======================

    public Appointment createAppointment(String apptId, String patientId, String doctorId,
                                         LocalDateTime dateTime, String notes) {
        String aid = validateNewId(apptId, findAppointment(apptId).isPresent(), "Appointment");
        Patient p = findPatient(patientId).orElseThrow(() -> new ClinicException("Register patient first."));
        Staff d = requireDoctor(doctorId);
        checkSlot(d, dateTime, null);
        Appointment a = new Appointment(aid, p.getId(), d.getId(), dateTime, notes == null ? "" : notes.trim());
        appointments.add(a);
        persist();
        return a;
    }

    public void cancelAppointment(String apptId) {
        Appointment a = requireAppointment(apptId);
        appointments.remove(a);
        persist();
    }

    public void updateAppointmentPatient(String apptId, String patientId) {
        Appointment a = requireAppointment(apptId);
        a.setPatientId(requirePatient(patientId).getId());
        persist();
    }

    public Staff updateAppointmentDoctor(String apptId, String doctorId) {
        Appointment a = requireAppointment(apptId);
        Staff d = requireDoctor(doctorId);
        checkSlot(d, a.getDateTime(), a.getApptId());
        a.setDoctorId(d.getId());
        persist();
        return d;
    }

    public void updateAppointmentDateTime(String apptId, LocalDateTime dateTime) {
        Appointment a = requireAppointment(apptId);
        checkSlot(requireDoctor(a.getDoctorId()), dateTime, a.getApptId());
        a.setDateTime(dateTime);
        persist();
    }

    public void updateAppointmentNotes(String apptId, String notes) {
        requireAppointment(apptId).setNotes(requireText(notes, "Notes"));
        persist();
    }

    /** Returns an existing booking for this doctor that overlaps the requested slot, if any. */
    public Optional<Appointment> findConflict(String doctorId, LocalDateTime dateTime, String ignoreApptId) {
        return appointments.stream()
                .filter(a -> a.getDoctorId().equalsIgnoreCase(doctorId))
                .filter(a -> ignoreApptId == null || !a.getApptId().equalsIgnoreCase(ignoreApptId))
                .filter(a -> Math.abs(Duration.between(a.getDateTime(), dateTime).toMinutes()) < SLOT_MINUTES)
                .findFirst();
    }

    /**
     * Throws if the slot is in the past or clashes with another booking for this doctor.
     * {@code ignoreApptId} lets an appointment be rescheduled without clashing with itself.
     */
    public void ensureSlotAvailable(String doctorId, LocalDateTime dateTime, String ignoreApptId) {
        checkSlot(requireDoctor(doctorId), dateTime, ignoreApptId);
    }

    private void checkSlot(Staff doctor, LocalDateTime dateTime, String ignoreApptId) {
        if (dateTime == null) throw new ClinicException("Date & time is required.");
        if (dateTime.isBefore(LocalDateTime.now(clock))) {
            throw new ClinicException("Cannot schedule in the past.");
        }
        findConflict(doctor.getId(), dateTime, ignoreApptId).ifPresent(c -> {
            throw new ClinicException("Doctor unavailable: " + doctor.getName() + " already has "
                    + c.getApptId() + " at " + c.getDateTime().format(Appointment.FORMAT) + ".");
        });
    }

    // ======================= MEDICINE INVENTORY =======================

    public Medicine addMedicine(String id, String name, int quantity, double price) {
        String mid = validateNewId(id, findMedicine(id).isPresent(), "Medicine");
        if (quantity < 0) throw new ClinicException("Quantity cannot be negative.");
        if (price < 0) throw new ClinicException("Price cannot be negative.");
        Medicine m = new Medicine(mid, requireText(name, "Name"), quantity, price);
        inventory.add(m);
        persist();
        return m;
    }

    public void restock(String id, int quantity) {
        Medicine m = requireMedicine(id);
        if (quantity <= 0) throw new ClinicException("Restock quantity must be greater than 0.");
        m.addStock(quantity);
        persist();
    }

    public void updateMedicineName(String id, String name) { requireMedicine(id).setName(requireText(name, "Name")); persist(); }

    public void updateMedicinePrice(String id, double price) {
        if (price < 0) throw new ClinicException("Price cannot be negative.");
        requireMedicine(id).setPrice(price);
        persist();
    }

    public void removeMedicine(String id) {
        inventory.remove(requireMedicine(id));
        persist();
    }

    // ======================= DOCTOR ACTIONS =======================

    /** A doctor is "assigned" to a patient once they share at least one appointment. */
    public boolean isAssigned(String doctorId, String patientId) {
        return appointments.stream().anyMatch(a -> a.getDoctorId().equalsIgnoreCase(doctorId)
                && a.getPatientId().equalsIgnoreCase(patientId == null ? "" : patientId.trim()));
    }

    public Set<String> assignedPatientIds(String doctorId) {
        return appointments.stream()
                .filter(a -> a.getDoctorId().equalsIgnoreCase(doctorId))
                .map(Appointment::getPatientId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<Appointment> appointmentsForDoctor(String doctorId) {
        return appointments.stream()
                .filter(a -> a.getDoctorId().equalsIgnoreCase(doctorId))
                .sorted(Comparator.comparing(Appointment::getDateTime))
                .collect(Collectors.toList());
    }

    public void addClinicalNote(String doctorId, String patientId, String note) {
        Patient p = requireAssignedPatient(doctorId, patientId,
                "You cannot add notes to a patient not assigned to you.");
        p.addMedicalHistory(requireText(note, "Medical note"));
        persist();
    }

    public void prescribe(String doctorId, String patientId, String medicineId, int quantity) {
        Patient p = requireAssignedPatient(doctorId, patientId, "Access Denied: You are not assigned to this patient.");
        Medicine m = findMedicine(medicineId).orElseThrow(() -> new ClinicException("Medicine not found."));
        if (quantity <= 0) throw new ClinicException("Quantity must be greater than 0.");
        if (quantity > m.getQuantity()) {
            throw new ClinicException("Not enough stock! (Available: " + m.getQuantity() + ")");
        }
        m.reduceStock(quantity);
        p.addMedicalHistory("Prescribed: " + m.getName() + " (Qty: " + quantity + ")");
        persist();
    }

    private Patient requireAssignedPatient(String doctorId, String patientId, String deniedMessage) {
        if (!isAssigned(doctorId, patientId)) throw new ClinicException(deniedMessage);
        return requirePatient(patientId);
    }

    public boolean isUpcoming(Appointment a) {
        return !a.getDateTime().isBefore(LocalDateTime.now(clock));
    }

    // ======================= REPORTS =======================

    /** Appointment count per doctor, including doctors with zero bookings. */
    public Map<Staff, Long> appointmentsPerDoctor() {
        Map<Staff, Long> result = new LinkedHashMap<>();
        for (Staff d : getDoctors()) {
            result.put(d, appointments.stream().filter(a -> a.getDoctorId().equalsIgnoreCase(d.getId())).count());
        }
        return result;
    }

    public int totalPatients() { return patients.size(); }

    public List<Medicine> lowStockMedicines() {
        return inventory.stream()
                .filter(m -> m.isLowStock(LOW_STOCK_THRESHOLD))
                .sorted(Comparator.comparingInt(Medicine::getQuantity))
                .collect(Collectors.toList());
    }

    public Map<String, Long> patientsByGender() {
        return patients.stream().collect(Collectors.groupingBy(Patient::getGender, TreeMap::new, Collectors.counting()));
    }

    /** Buckets: 0-17, 18-39, 40-59, 60+. */
    public Map<String, Long> patientsByAgeGroup() {
        Map<String, Long> groups = new LinkedHashMap<>();
        for (String g : List.of("0-17", "18-39", "40-59", "60+")) groups.put(g, 0L);
        for (Patient p : patients) groups.merge(ageGroup(p.getAge()), 1L, Long::sum);
        return groups;
    }

    static String ageGroup(int age) {
        if (age < 18) return "0-17";
        if (age < 40) return "18-39";
        if (age < 60) return "40-59";
        return "60+";
    }

    public double averagePatientAge() {
        return patients.stream().mapToInt(Patient::getAge).average().orElse(0);
    }

    // ======================= VALIDATION & HELPERS =======================

    private Patient requirePatient(String id) {
        return findPatient(id).orElseThrow(() -> new ClinicException("Patient ID [" + clean(id) + "] not found."));
    }

    private Staff requireStaff(String id) {
        return findStaff(id).orElseThrow(() -> new ClinicException("Staff ID [" + clean(id) + "] not found."));
    }

    private Staff requireDoctor(String id) {
        return findDoctor(id).orElseThrow(() -> new ClinicException("Invalid Doctor ID."));
    }

    private Appointment requireAppointment(String id) {
        return findAppointment(id).orElseThrow(() -> new ClinicException("Appointment ID [" + clean(id) + "] not found."));
    }

    private Medicine requireMedicine(String id) {
        return findMedicine(id).orElseThrow(() -> new ClinicException("Medicine not found."));
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }

    /** IDs are stored upper-case (p011 becomes P011) and must look like P001, S12, A0003. */
    public static String validateNewId(String id, boolean exists, String label) {
        String normalized = clean(id).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new ClinicException("ID cannot be empty.");
        if (!ID_PATTERN.matcher(normalized).matches()) {
            throw new ClinicException("Invalid ID format. Use letters followed by digits, e.g. P001.");
        }
        if (exists) throw new ClinicException(label + " ID [" + normalized + "] already exists!");
        return normalized;
    }

    public static String requireText(String value, String field) {
        String v = clean(value);
        if (v.isEmpty()) throw new ClinicException(field + " cannot be empty.");
        return v;
    }

    public static String validatePhone(String phone) {
        String digits = clean(phone).replaceAll("[\\s-]", "");
        if (!PHONE_PATTERN.matcher(digits).matches()) {
            throw new ClinicException("Invalid phone number. Use 9-13 digits, e.g. 0123456789.");
        }
        return digits;
    }

    public static int validateAge(int age) {
        if (age < 0 || age > 130) throw new ClinicException("Age must be between 0 and 130.");
        return age;
    }

    public static String validateGender(String gender) {
        String g = clean(gender).toUpperCase(Locale.ROOT);
        if (!g.equals("M") && !g.equals("F")) throw new ClinicException("Enter M or F.");
        return g;
    }

    public static String validateRole(String role) {
        String r = clean(role);
        if (r.equalsIgnoreCase(Staff.ROLE_DOCTOR)) return Staff.ROLE_DOCTOR;
        if (r.equalsIgnoreCase(Staff.ROLE_RECEPTIONIST)) return Staff.ROLE_RECEPTIONIST;
        throw new ClinicException("Role must be Doctor or Receptionist.");
    }

    public static String validateEmail(String email) {
        String e = clean(email);
        if (!EMAIL_PATTERN.matcher(e).matches()) throw new ClinicException("Invalid email address.");
        return e;
    }

    private void persist() {
        try {
            store.savePatients(patients);
            store.saveStaff(staff);
            store.saveAppointments(appointments);
            store.saveMedicine(inventory);
        } catch (IOException e) {
            throw new UncheckedIOException("Error saving files: " + e.getMessage(), e);
        }
    }
}
