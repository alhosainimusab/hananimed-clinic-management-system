package com.hananimed.persistence;

import com.hananimed.model.Appointment;
import com.hananimed.model.Medicine;
import com.hananimed.model.Patient;
import com.hananimed.model.Staff;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Loads and saves the four clinic data files (UTF-8 CSV with a header row).
 * Malformed lines are skipped and reported through {@link #getWarnings()}
 * instead of crashing the program. Saves go to a temporary file first and are
 * then moved into place, so an interrupted save cannot leave a half-written file.
 */
public class ClinicDataManager {
    public static final String PATIENT_FILE = "patients.csv";
    public static final String STAFF_FILE = "staff.csv";
    public static final String APPOINTMENT_FILE = "appointments.csv";
    public static final String MEDICINE_FILE = "medicine.csv";

    static final List<String> PATIENT_HEADER = List.of("id", "name", "phone", "age", "gender", "medical_history");
    static final List<String> STAFF_HEADER = List.of("id", "name", "phone", "role", "email");
    static final List<String> APPOINTMENT_HEADER = List.of("id", "patient_id", "doctor_id", "date_time", "notes");
    static final List<String> MEDICINE_HEADER = List.of("id", "name", "quantity", "price");

    private final Path dataDir;
    private final List<String> warnings = new ArrayList<>();

    public ClinicDataManager(Path dataDir) {
        this.dataDir = dataDir;
    }

    public Path getDataDir() { return dataDir; }

    /** Warnings collected while loading (e.g. skipped malformed lines). */
    public List<String> getWarnings() { return Collections.unmodifiableList(warnings); }

    // ---------------- load ----------------

    public List<Patient> loadPatients() throws IOException {
        return load(PATIENT_FILE, 5, f -> {
            Patient p = new Patient(f.get(0).trim(), f.get(1).trim(), f.get(2).trim(),
                    Integer.parseInt(f.get(3).trim()), f.get(4).trim().toUpperCase());
            if (f.size() > 5) CsvUtil.splitList(f.get(5)).forEach(p::addMedicalHistory);
            return p;
        });
    }

    public List<Staff> loadStaff() throws IOException {
        return load(STAFF_FILE, 5, f -> new Staff(f.get(0).trim(), f.get(1).trim(), f.get(2).trim(),
                f.get(3).trim(), f.get(4).trim()));
    }

    public List<Appointment> loadAppointments() throws IOException {
        return load(APPOINTMENT_FILE, 4, f -> new Appointment(f.get(0).trim(), f.get(1).trim(), f.get(2).trim(),
                LocalDateTime.parse(f.get(3).trim(), Appointment.FORMAT), f.size() > 4 ? f.get(4).trim() : ""));
    }

    public List<Medicine> loadMedicine() throws IOException {
        return load(MEDICINE_FILE, 4, f -> new Medicine(f.get(0).trim(), f.get(1).trim(),
                Integer.parseInt(f.get(2).trim()), Double.parseDouble(f.get(3).trim())));
    }

    private <T> List<T> load(String fileName, int minFields, Function<List<String>, T> parser) throws IOException {
        List<T> result = new ArrayList<>();
        Path file = dataDir.resolve(fileName);
        if (!Files.exists(file)) return result;

        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = br.readLine()) != null) {
                lineNo++;
                if (lineNo == 1 && line.startsWith("\uFEFF")) line = line.substring(1); // strip BOM
                if (line.isBlank()) continue;
                try {
                    List<String> fields = CsvUtil.parseLine(line);
                    if (lineNo == 1 && fields.get(0).trim().equalsIgnoreCase("id")) continue; // header row
                    if (fields.size() < minFields) {
                        throw new IllegalArgumentException("expected at least " + minFields
                                + " fields but found " + fields.size());
                    }
                    result.add(parser.apply(fields));
                } catch (RuntimeException e) {
                    warnings.add(String.format("%s line %d skipped (%s)", fileName, lineNo, e.getMessage()));
                }
            }
        }
        return result;
    }

    // ---------------- save ----------------

    public void savePatients(List<Patient> list) throws IOException {
        save(PATIENT_FILE, PATIENT_HEADER, list, p -> List.of(p.getId(), p.getName(), p.getPhone(),
                String.valueOf(p.getAge()), p.getGender(), CsvUtil.joinList(p.getMedicalHistory())));
    }

    public void saveStaff(List<Staff> list) throws IOException {
        save(STAFF_FILE, STAFF_HEADER, list, s -> List.of(s.getId(), s.getName(), s.getPhone(),
                s.getRole(), s.getEmail()));
    }

    public void saveAppointments(List<Appointment> list) throws IOException {
        save(APPOINTMENT_FILE, APPOINTMENT_HEADER, list, a -> List.of(a.getApptId(), a.getPatientId(),
                a.getDoctorId(), a.getDateTime().format(Appointment.FORMAT), a.getNotes()));
    }

    public void saveMedicine(List<Medicine> list) throws IOException {
        save(MEDICINE_FILE, MEDICINE_HEADER, list, m -> List.of(m.getId(), m.getName(),
                String.valueOf(m.getQuantity()), String.format(java.util.Locale.ROOT, "%.2f", m.getPrice())));
    }

    private <T> void save(String fileName, List<String> header, List<T> items,
                          Function<T, List<String>> toFields) throws IOException {
        Files.createDirectories(dataDir);
        Path target = dataDir.resolve(fileName);
        Path temp = dataDir.resolve(fileName + ".tmp");
        try (BufferedWriter bw = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            bw.write(CsvUtil.toLine(header));
            bw.newLine();
            for (T item : items) {
                bw.write(CsvUtil.toLine(toFields.apply(item)));
                bw.newLine();
            }
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
