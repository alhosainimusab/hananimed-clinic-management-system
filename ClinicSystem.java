package com.hananimed.ui;

import com.hananimed.model.Appointment;
import com.hananimed.model.Medicine;
import com.hananimed.model.Patient;
import com.hananimed.model.Staff;
import com.hananimed.persistence.ClinicDataManager;
import com.hananimed.service.ClinicException;
import com.hananimed.service.ClinicService;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Console front end for HananiMed. It only handles input and output;
 * every rule is delegated to {@link ClinicService}.
 */
public class ClinicSystem {
    private static final String OK = "[OK] ";
    private static final String ERR = "[ERROR] ";
    private static final String WARN = "[!] ";

    private final ClinicService service;
    private final Scanner sc;
    private final PrintStream out;

    /** Raised when the input stream ends (e.g. piped input) so the program can exit cleanly. */
    private static final class EndOfInput extends RuntimeException {
        private static final long serialVersionUID = 1L;

        EndOfInput() { super(null, null, false, false); }
    }

    public ClinicSystem(ClinicService service, Scanner sc, PrintStream out) {
        this.service = service;
        this.sc = sc;
        this.out = out;
    }

    public void run() {
        try {
            loginLoop();
        } catch (EndOfInput e) {
            out.println();
        }
        out.println("System closed. Goodbye.");
    }

    private void loginLoop() {
        while (true) {
            out.println("\n=== LOGIN: HANANIMED CLINIC MANAGEMENT SYSTEM ===");
            out.println("1. Administrator");
            out.println("2. Doctor");
            out.println("3. Receptionist");
            out.println("0. Shutdown");
            String role = prompt("Select Role> ");
            switch (role) {
                case "1": adminMenu(); break;
                case "2": doctorLogin(); break;
                case "3": receptionistMenu(); break;
                case "0": return;
                default: invalid();
            }
        }
    }

    private void doctorLogin() {
        String id = prompt("Enter Doctor ID to Login: ");
        Optional<Staff> doc = service.findDoctor(id);
        if (doc.isPresent()) doctorMenu(doc.get());
        else out.println(ERR + "Access Denied: Invalid Doctor ID.");
    }

    // ======================= ROLE MENUS =======================

    private void adminMenu() {
        while (true) {
            out.println("\n-- ADMINISTRATOR (SUPER USER) MENU --");
            out.println("1. Patient Management (Full Access)");
            out.println("2. Staff Management (Add/Remove)");
            out.println("3. Appointment Management");
            out.println("4. Medicine Inventory Management");
            out.println("5. View Operational Reports");
            out.println("0. Logout");
            switch (prompt("Choice> ")) {
                case "1": patientMenu(); break;
                case "2": staffMenu(); break;
                case "3": appointmentMenu(); break;
                case "4": inventoryMenu(); break;
                case "5": reportsMenu(); break;
                case "0": return;
                default: invalid();
            }
        }
    }

    private void doctorMenu(Staff doc) {
        while (true) {
            out.println("\n-- DOCTOR MENU: " + doc.getName().toUpperCase() + " --");
            out.println("1. Add Clinical Note");
            out.println("2. Prescribe Medicine");
            out.println("3. View Assigned Patient History");
            out.println("4. View My Appointments");
            out.println("0. Logout");
            switch (prompt("Choice> ")) {
                case "1": action(() -> addClinicalNote(doc)); break;
                case "2": action(() -> prescribeMedicine(doc)); break;
                case "3": action(() -> viewAssignedHistory(doc)); break;
                case "4": viewMyAppointments(doc); break;
                case "0": return;
                default: invalid();
            }
        }
    }

    private void receptionistMenu() {
        while (true) {
            out.println("\n-- RECEPTIONIST MAIN MENU --");
            out.println("1. Register New Patient");
            out.println("2. Multi-Search Patient Records");
            out.println("3. Manage Appointments");
            out.println("0. Logout");
            switch (prompt("Choice> ")) {
                case "1": action(this::addPatient); break;
                case "2": searchPatients(); break;
                case "3": appointmentMenu(); break;
                case "0": return;
                default: invalid();
            }
        }
    }

    // ======================= MODULE MENUS =======================

    private void patientMenu() {
        while (true) {
            out.println("\n-- Patient Management --");
            out.println("1. Add Patient | 2. Remove | 3. Update | 4. Multi-Search | 5. Display | 0. Back");
            switch (prompt("Choice> ")) {
                case "1": action(this::addPatient); break;
                case "2": action(this::removePatient); break;
                case "3": action(this::updatePatient); break;
                case "4": searchPatients(); break;
                case "5": display(service.getPatients().stream().map(Patient::getDetails).toList(), "patients"); break;
                case "0": return;
                default: invalid();
            }
        }
    }

    private void staffMenu() {
        while (true) {
            out.println("\n-- Staff Management --");
            out.println("1. Add Staff | 2. Remove | 3. Update | 4. Display | 0. Back");
            switch (prompt("Choice> ")) {
                case "1": action(this::addStaff); break;
                case "2": action(this::removeStaff); break;
                case "3": action(this::updateStaff); break;
                case "4": display(service.getStaff().stream().map(Staff::getDetails).toList(), "staff"); break;
                case "0": return;
                default: invalid();
            }
        }
    }

    private void appointmentMenu() {
        while (true) {
            out.println("\n-- Appointment Management --");
            out.println("1. Create | 2. Cancel | 3. Update | 4. Multi-Search | 5. Display | 0. Back");
            switch (prompt("Choice> ")) {
                case "1": action(this::createAppointment); break;
                case "2": action(this::cancelAppointment); break;
                case "3": action(this::updateAppointment); break;
                case "4": searchAppointments(); break;
                case "5": display(service.getAppointments().stream().map(Appointment::getDetails).toList(), "appointments"); break;
                case "0": return;
                default: invalid();
            }
        }
    }

    private void inventoryMenu() {
        while (true) {
            out.println("\n-- Medicine Inventory Management --");
            out.println("1. Add New Medicine");
            out.println("2. View All Stock");
            out.println("3. Restock Quantity");
            out.println("4. Update Medicine Details (Name/Price)");
            out.println("5. Remove Medicine Record");
            out.println("0. Back");
            switch (prompt("Choice> ")) {
                case "1": action(this::addMedicine); break;
                case "2": displayInventory(); break;
                case "3": action(this::restock); break;
                case "4": action(this::updateMedicine); break;
                case "5": action(this::removeMedicine); break;
                case "0": return;
                default: invalid();
            }
        }
    }

    private void reportsMenu() {
        while (true) {
            out.println("\n-- Operational Reports --");
            out.println("1. Appointments per Doctor (Live Count)");
            out.println("2. Total Patient Count");
            out.println("3. Low-Stock Medicines (below " + ClinicService.LOW_STOCK_THRESHOLD + ")");
            out.println("4. Patient Demographics");
            out.println("0. Back");
            switch (prompt("Choice> ")) {
                case "1": reportAppointmentsPerDoctor(); break;
                case "2": out.println("\n[Report] Total Registered Patients: " + service.totalPatients()); break;
                case "3": reportLowStock(); break;
                case "4": reportDemographics(); break;
                case "0": return;
                default: invalid();
            }
        }
    }

    // ======================= PATIENTS =======================

    private void addPatient() {
        out.println("\n--- Register New Patient ---");
        String id = readValid("Enter Patient ID (e.g., P001): ",
                v -> ClinicService.validateNewId(v, service.findPatient(v).isPresent(), "Patient"));
        String name = readValid("Enter Name: ", v -> ClinicService.requireText(v, "Name"));
        String phone = readValid("Enter Phone: ", ClinicService::validatePhone);
        int age = readInt("Enter Age: ", 0, 130);
        String gender = readValid("Enter Gender (M/F): ", ClinicService::validateGender);

        out.println("\n--- Please Review Patient Details ---");
        out.println("ID: " + id + " | Name: " + name + " | Phone: " + phone + " | Age: " + age + " | Gender: " + gender);
        if (confirm("\nConfirm registration? (Y/N): ")) {
            service.registerPatient(id, name, phone, age, gender);
            out.println(OK + "Patient registered successfully.");
        } else {
            out.println(WARN + "Operation cancelled.");
        }
    }

    private void removePatient() {
        Patient p = service.findPatient(prompt("Enter Patient ID to remove: "))
                .orElseThrow(() -> new ClinicException("Patient not found."));
        long appts = service.getAppointments().stream().filter(a -> a.getPatientId().equals(p.getId())).count();
        out.println("Removing: " + p.getDetails() + (appts > 0 ? " and " + appts + " appointment(s)" : ""));
        if (confirm("Confirm removal? (Y/N): ")) {
            service.removePatient(p.getId());
            out.println(OK + "Record removed.");
        } else {
            out.println(WARN + "Operation cancelled.");
        }
    }

    private void updatePatient() {
        Patient p = service.findPatient(prompt("Enter Patient ID to update: "))
                .orElseThrow(() -> new ClinicException("Patient not found."));
        out.println("Updating: " + p.getDetails());
        out.println("1. Name | 2. Phone | 3. Age | 4. Gender | 0. Cancel");
        switch (prompt("Select field to update> ")) {
            case "1": service.updatePatientName(p.getId(), readValid("New Name: ", v -> ClinicService.requireText(v, "Name"))); break;
            case "2": service.updatePatientPhone(p.getId(), readValid("New Phone: ", ClinicService::validatePhone)); break;
            case "3": service.updatePatientAge(p.getId(), readInt("New Age: ", 0, 130)); break;
            case "4": service.updatePatientGender(p.getId(), readValid("New Gender (M/F): ", ClinicService::validateGender)); break;
            default: out.println(WARN + "Operation cancelled."); return;
        }
        out.println(OK + "Patient record updated.");
    }

    private void searchPatients() {
        String[] ids = prompt("Enter Patient IDs (comma separated): ").split(",");
        out.println("\n--- Search Results ---");
        for (String id : ids) {
            Optional<Patient> p = service.findPatient(id);
            out.println(p.map(x -> OK + x.getDetails()).orElse(ERR + "ID [" + id.trim() + "]: Not found."));
        }
    }

    // ======================= STAFF =======================

    private void addStaff() {
        out.println("\n--- Add New Staff Member ---");
        String id = readValid("Enter Staff ID (e.g., S001): ",
                v -> ClinicService.validateNewId(v, service.findStaff(v).isPresent(), "Staff"));
        String name = readValid("Enter Name: ", v -> ClinicService.requireText(v, "Name"));
        String phone = readValid("Enter Phone: ", ClinicService::validatePhone);
        String role = readValid("Enter Role (Doctor/Receptionist): ", ClinicService::validateRole);
        String email = readValid("Enter Email: ", ClinicService::validateEmail);

        out.println("\n--- Please Review Staff Details ---");
        out.println("ID: " + id + " | Name: " + name + " | Role: " + role + " | Email: " + email);
        if (confirm("\nConfirm to add this staff? (Y/N): ")) {
            service.addStaff(id, name, phone, role, email);
            out.println(OK + "Staff member added successfully.");
        } else {
            out.println(WARN + "Operation cancelled.");
        }
    }

    private void removeStaff() {
        Staff s = service.findStaff(prompt("Enter Staff ID to remove: "))
                .orElseThrow(() -> new ClinicException("Staff member not found."));
        long appts = service.getAppointments().stream().filter(a -> a.getDoctorId().equals(s.getId())).count();
        out.println("Removing: " + s.getDetails() + (appts > 0 ? " and " + appts + " appointment(s)" : ""));
        if (confirm("Confirm removal? (Y/N): ")) {
            service.removeStaff(s.getId());
            out.println(OK + "Staff removed.");
        } else {
            out.println(WARN + "Operation cancelled.");
        }
    }

    private void updateStaff() {
        Staff s = service.findStaff(prompt("Enter Staff ID to update: "))
                .orElseThrow(() -> new ClinicException("Staff member not found."));
        out.println("Updating: " + s.getDetails());
        out.println("1. Name | 2. Phone | 3. Email | 4. Role | 0. Cancel");
        switch (prompt("Select field> ")) {
            case "1": service.updateStaffName(s.getId(), readValid("New Name: ", v -> ClinicService.requireText(v, "Name"))); break;
            case "2": service.updateStaffPhone(s.getId(), readValid("New Phone: ", ClinicService::validatePhone)); break;
            case "3": service.updateStaffEmail(s.getId(), readValid("New Email: ", ClinicService::validateEmail)); break;
            case "4": service.updateStaffRole(s.getId(), readValid("New Role (Doctor/Receptionist): ", ClinicService::validateRole)); break;
            default: out.println(WARN + "Operation cancelled."); return;
        }
        out.println(OK + "Staff record updated.");
    }

    // ======================= APPOINTMENTS =======================

    private void createAppointment() {
        String aid = readValid("Enter Appt ID: ",
                v -> ClinicService.validateNewId(v, service.findAppointment(v).isPresent(), "Appointment"));
        String pid = prompt("Enter Patient ID: ");
        if (service.findPatient(pid).isEmpty()) throw new ClinicException("Register patient first.");

        out.println("Available Doctors:");
        service.getDoctors().forEach(d -> out.println("  " + d.getId() + ": " + d.getName()));
        String did = prompt("Enter Doctor ID: ");
        if (service.findDoctor(did).isEmpty()) throw new ClinicException("Invalid Doctor ID.");

        LocalDateTime dt = readAvailableSlot("Enter Date (yyyy-MM-dd HH:mm): ", did, null);
        if (dt == null) { out.println(WARN + "Operation cancelled."); return; }
        String notes = prompt("Notes: ");
        Appointment a = service.createAppointment(aid, pid, did, dt, notes);
        out.println(OK + "Appointment " + a.getApptId() + " created.");
    }

    private void cancelAppointment() {
        Appointment a = service.findAppointment(prompt("Enter Appointment ID to cancel: "))
                .orElseThrow(() -> new ClinicException("Appointment ID not found."));
        out.println("Cancelling: " + a.getDetails());
        if (confirm("Confirm cancellation? (Y/N): ")) {
            service.cancelAppointment(a.getApptId());
            out.println(OK + "Appointment " + a.getApptId() + " has been cancelled.");
        } else {
            out.println(WARN + "Operation cancelled.");
        }
    }

    private void updateAppointment() {
        Appointment a = service.findAppointment(prompt("Enter Appointment ID to update: "))
                .orElseThrow(() -> new ClinicException("Appointment ID not found."));
        out.println("Current Record: " + a.getDetails());
        out.println("1. Patient ID | 2. Doctor ID | 3. Date & Time | 4. Notes | 0. Cancel");
        switch (prompt("Choice> ")) {
            case "1":
                service.updateAppointmentPatient(a.getApptId(), prompt("New Patient ID: "));
                out.println(OK + "Patient ID updated.");
                break;
            case "2":
                Staff d = service.updateAppointmentDoctor(a.getApptId(), prompt("New Doctor ID: "));
                out.println(OK + "Doctor updated to " + d.getName() + ".");
                break;
            case "3":
                LocalDateTime dt = readAvailableSlot("New Date (yyyy-MM-dd HH:mm): ", a.getDoctorId(), a.getApptId());
                if (dt == null) { out.println(WARN + "Operation cancelled."); return; }
                service.updateAppointmentDateTime(a.getApptId(), dt);
                out.println(OK + "Date & Time updated.");
                break;
            case "4":
                service.updateAppointmentNotes(a.getApptId(), readValid("New Notes: ", v -> ClinicService.requireText(v, "Notes")));
                out.println(OK + "Notes updated.");
                break;
            default:
                out.println(WARN + "Operation cancelled.");
        }
    }

    private void searchAppointments() {
        String[] ids = prompt("Enter Appointment IDs (comma separated): ").split(",");
        out.println("\n--- Search Results ---");
        for (String id : ids) {
            Optional<Appointment> a = service.findAppointment(id);
            out.println(a.map(x -> OK + x.getDetails()).orElse(ERR + "ID [" + id.trim() + "]: Not found."));
        }
    }

    // ======================= INVENTORY =======================

    private void addMedicine() {
        String id = readValid("Enter Medicine ID (e.g., M001): ",
                v -> ClinicService.validateNewId(v, service.findMedicine(v).isPresent(), "Medicine"));
        String name = readValid("Enter Name: ", v -> ClinicService.requireText(v, "Name"));
        int qty = readInt("Enter Quantity: ", 0, Integer.MAX_VALUE);
        double price = readDouble("Enter Unit Price (RM): ");
        service.addMedicine(id, name, qty, price);
        out.println(OK + "Medicine added.");
    }

    private void restock() {
        Medicine m = service.findMedicine(prompt("Enter Medicine ID: "))
                .orElseThrow(() -> new ClinicException("Medicine not found."));
        out.println("Current: " + m.getDetails());
        service.restock(m.getId(), readInt("Add quantity: ", 1, Integer.MAX_VALUE));
        out.println(OK + "Stock updated. New quantity: " + m.getQuantity());
    }

    private void updateMedicine() {
        Medicine m = service.findMedicine(prompt("Enter Medicine ID to update: "))
                .orElseThrow(() -> new ClinicException("Medicine not found."));
        out.println("Updating: " + m.getDetails());
        String newName = prompt("Enter New Name (or leave blank to keep current): ");
        if (!newName.isEmpty()) {
            service.updateMedicineName(m.getId(), newName);
            out.println(OK + "Name updated to " + m.getName());
        }
        while (true) {
            String priceText = prompt("Enter New Price (or leave blank to keep current): ");
            if (priceText.isEmpty()) break;
            try {
                service.updateMedicinePrice(m.getId(), Double.parseDouble(priceText));
                out.printf("%sPrice updated to RM%.2f%n", OK, m.getPrice());
                break;
            } catch (NumberFormatException e) {
                out.println(ERR + "Enter a valid number.");
            }
        }
        out.println("Result: " + m.getDetails());
    }

    private void removeMedicine() {
        Medicine m = service.findMedicine(prompt("Enter Medicine ID to remove: "))
                .orElseThrow(() -> new ClinicException("Medicine not found."));
        out.println("Removing: " + m.getDetails());
        if (confirm("Confirm removal? (Y/N): ")) {
            service.removeMedicine(m.getId());
            out.println(OK + "Medicine removed.");
        } else {
            out.println(WARN + "Operation cancelled.");
        }
    }

    private void displayInventory() {
        display(service.getInventory().stream().map(Medicine::getDetails).toList(), "medicines");
    }

    // ======================= DOCTOR =======================

    private void addClinicalNote(Staff doc) {
        String pid = prompt("Enter Patient ID for Consultation: ");
        if (!service.isAssigned(doc.getId(), pid)) {
            throw new ClinicException("You cannot add notes to a patient not assigned to you.");
        }
        Patient p = service.findPatient(pid).orElseThrow(() -> new ClinicException("Patient not found."));
        out.println("Consulting: " + p.getName());
        service.addClinicalNote(doc.getId(), p.getId(), readValid("Enter Medical Note: ", v -> ClinicService.requireText(v, "Note")));
        out.println(OK + "Medical note added and synced.");
    }

    private void prescribeMedicine(Staff doc) {
        String pid = prompt("Enter Patient ID for Prescription: ");
        if (!service.isAssigned(doc.getId(), pid)) {
            throw new ClinicException("Access Denied: You are not assigned to this patient.");
        }
        displayInventory();
        String mid = prompt("Enter Medicine ID: ");
        if (service.findMedicine(mid).isEmpty()) throw new ClinicException("Medicine not found.");
        int qty = readInt("Enter Quantity to prescribe: ", 1, Integer.MAX_VALUE);
        service.prescribe(doc.getId(), pid, mid, qty);
        out.println(OK + "Medicine assigned and inventory updated.");
    }

    private void viewAssignedHistory(Staff doc) {
        out.println("\n--- Patients Assigned to " + doc.getName() + " ---");
        Set<String> ids = service.assignedPatientIds(doc.getId());
        if (ids.isEmpty()) {
            out.println("No patients assigned to you yet.");
            return;
        }
        out.println("Your Assigned Patient IDs: " + ids);
        String pid = prompt("Enter specific Patient ID to view full history: ");
        if (!service.isAssigned(doc.getId(), pid)) {
            throw new ClinicException("Access Denied: This patient is not assigned to you.");
        }
        Patient p = service.findPatient(pid).orElseThrow(() -> new ClinicException("Patient not found."));
        out.println("\n--- FULL MEDICAL RECORD: " + p.getName().toUpperCase() + " ---");
        out.println("Age/Gender : " + p.getAge() + " / " + p.getGender());
        out.println("Contact    : " + p.getPhone());
        out.println("Clinical Notes:");
        if (p.getMedicalHistory().isEmpty()) out.println("- (none)");
        p.getMedicalHistory().forEach(n -> out.println("- " + n));
    }

    private void viewMyAppointments(Staff doc) {
        List<Appointment> list = service.appointmentsForDoctor(doc.getId());
        out.println("\n--- Appointments for " + doc.getName() + " ---");
        if (list.isEmpty()) {
            out.println("No appointments scheduled.");
            return;
        }
        for (Appointment a : list) {
            String patient = service.findPatient(a.getPatientId()).map(Patient::getName).orElse("?");
            out.printf("%s  %-5s %-6s %-18s %s%s%n", a.getDateTime().format(Appointment.FORMAT), a.getApptId(),
                    a.getPatientId(), patient, a.getNotes(), service.isUpcoming(a) ? "" : "  (past)");
        }
    }

    // ======================= REPORTS =======================

    private void reportAppointmentsPerDoctor() {
        out.println("\n-- Summary: Appointments per Doctor --");
        Map<Staff, Long> counts = service.appointmentsPerDoctor();
        if (counts.isEmpty()) out.println("No doctors registered.");
        counts.forEach((d, c) -> out.printf("Doctor: %-20s | Count: %d%n", d.getName(), c));
    }

    private void reportLowStock() {
        out.println("\n-- Low-Stock Medicines (below " + ClinicService.LOW_STOCK_THRESHOLD + " units) --");
        List<Medicine> low = service.lowStockMedicines();
        if (low.isEmpty()) out.println("All medicines are sufficiently stocked.");
        low.forEach(m -> out.printf("%-5s %-32s Qty: %d%n", m.getId(), m.getName(), m.getQuantity()));
    }

    private void reportDemographics() {
        out.println("\n-- Patient Demographics --");
        if (service.totalPatients() == 0) {
            out.println("No patients registered.");
            return;
        }
        out.printf("Total patients : %d%n", service.totalPatients());
        out.printf("Average age    : %.1f%n", service.averagePatientAge());
        out.println("By gender      : " + formatCounts(service.patientsByGender()));
        out.println("By age group   : " + formatCounts(service.patientsByAgeGroup()));
    }

    private static String formatCounts(Map<String, Long> counts) {
        StringBuilder sb = new StringBuilder();
        counts.forEach((k, v) -> sb.append(sb.length() == 0 ? "" : " | ").append(k).append(": ").append(v));
        return sb.toString();
    }

    // ======================= INPUT HELPERS =======================

    /** Runs one action and turns business-rule failures into a readable error line. */
    private void action(Runnable r) {
        try {
            r.run();
        } catch (ClinicException e) {
            out.println(ERR + e.getMessage());
        } catch (UncheckedIOException e) {
            out.println(ERR + e.getMessage());
        }
    }

    private String readLine() {
        if (!sc.hasNextLine()) throw new EndOfInput();
        return sc.nextLine().trim();
    }

    private String prompt(String text) {
        out.print(text);
        return readLine();
    }

    private String readValid(String text, UnaryOperator<String> validator) {
        while (true) {
            try {
                return validator.apply(prompt(text));
            } catch (ClinicException e) {
                out.println(ERR + e.getMessage());
            }
        }
    }

    private int readInt(String text, int min, int max) {
        while (true) {
            try {
                int v = Integer.parseInt(prompt(text));
                if (v >= min && v <= max) return v;
                out.println(ERR + (max == Integer.MAX_VALUE
                        ? "Enter a number of at least " + min + "."
                        : "Enter a number between " + min + " and " + max + "."));
            } catch (NumberFormatException e) {
                out.println(ERR + "Enter a valid number.");
            }
        }
    }

    private double readDouble(String text) {
        while (true) {
            try {
                double v = Double.parseDouble(prompt(text));
                if (v >= 0) return v;
                out.println(ERR + "Value cannot be negative.");
            } catch (NumberFormatException e) {
                out.println(ERR + "Enter a valid number.");
            }
        }
    }

    /** Returns null if the user leaves the field blank (cancel). */
    private LocalDateTime readDateTime(String text) {
        while (true) {
            String v = prompt(text);
            if (v.isEmpty()) return null;
            try {
                return LocalDateTime.parse(v, Appointment.FORMAT);
            } catch (DateTimeParseException e) {
                out.println(ERR + "Invalid date format. Use yyyy-MM-dd HH:mm, or leave blank to cancel.");
            }
        }
    }

    /** Re-prompts until the doctor is free at the entered time; blank input cancels (returns null). */
    private LocalDateTime readAvailableSlot(String text, String doctorId, String ignoreApptId) {
        while (true) {
            LocalDateTime dt = readDateTime(text);
            if (dt == null) return null;
            try {
                service.ensureSlotAvailable(doctorId, dt, ignoreApptId);
                return dt;
            } catch (ClinicException e) {
                out.println(ERR + e.getMessage() + " Try another time, or leave blank to cancel.");
            }
        }
    }

    private boolean confirm(String text) {
        return prompt(text).equalsIgnoreCase("Y");
    }

    private void invalid() {
        out.println("Invalid selection.");
    }

    private void display(List<String> rows, String what) {
        if (rows.isEmpty()) out.println("No " + what + " found.");
        rows.forEach(out::println);
    }

    // ======================= ENTRY POINT =======================

    /**
     * Data directory: first program argument, else -Dclinic.data.dir, else ./data.
     */
    public static void main(String[] args) throws IOException {
        Path dataDir = Paths.get(args.length > 0 ? args[0] : System.getProperty("clinic.data.dir", "data"));
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        ClinicDataManager store = new ClinicDataManager(dataDir);
        ClinicService service = new ClinicService(store, Clock.systemDefaultZone());

        out.println("Data directory: " + dataDir.toAbsolutePath().normalize());
        service.getLoadWarnings().forEach(w -> out.println(WARN + "Warning: " + w));
        new ClinicSystem(service, new Scanner(System.in, StandardCharsets.UTF_8), out).run();
    }
}
