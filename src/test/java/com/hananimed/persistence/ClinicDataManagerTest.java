package com.hananimed.persistence;

import com.hananimed.model.Appointment;
import com.hananimed.model.Medicine;
import com.hananimed.model.Patient;
import com.hananimed.model.Staff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClinicDataManagerTest {

    @TempDir
    Path dir;

    @Test
    void missingFilesGiveEmptyLists() throws Exception {
        ClinicDataManager store = new ClinicDataManager(dir.resolve("not-created-yet"));
        assertTrue(store.loadPatients().isEmpty());
        assertTrue(store.loadAppointments().isEmpty());
        assertTrue(store.getWarnings().isEmpty());
    }

    @Test
    void saveCreatesDirectoryWithHeaderRow() throws Exception {
        Path sub = dir.resolve("new-data");
        new ClinicDataManager(sub).saveStaff(List.of());
        assertEquals(List.of("id,name,phone,role,email"),
                Files.readAllLines(sub.resolve(ClinicDataManager.STAFF_FILE)));
    }

    @Test
    void allEntitiesRoundTrip() throws Exception {
        ClinicDataManager store = new ClinicDataManager(dir);
        Patient p = new Patient("P001", "Aisyah Rahman", "0120000001", 34, "F");
        p.addMedicalHistory("Fever, cough; mild");
        p.addMedicalHistory("Prescribed: Paracetamol (Qty: 2)");
        store.savePatients(List.of(p));
        store.saveStaff(List.of(new Staff("S001", "Dr. Hanani", "0130000001", "Doctor", "h@x.example")));
        store.saveAppointments(List.of(new Appointment("A001", "P001", "S001",
                LocalDateTime.of(2027, 1, 11, 9, 0), "Back pain, 2 weeks")));
        store.saveMedicine(List.of(new Medicine("M001", "Paracetamol", 790, 0.2)));

        ClinicDataManager reload = new ClinicDataManager(dir);
        Patient loaded = reload.loadPatients().get(0);
        assertEquals("Aisyah Rahman", loaded.getName());
        assertEquals(List.of("Fever, cough; mild", "Prescribed: Paracetamol (Qty: 2)"), loaded.getMedicalHistory());
        assertEquals("Doctor", reload.loadStaff().get(0).getRole());
        assertEquals("Back pain, 2 weeks", reload.loadAppointments().get(0).getNotes());
        assertEquals(0.2, reload.loadMedicine().get(0).getPrice(), 1e-9);
        assertTrue(reload.getWarnings().isEmpty());
    }

    @Test
    void malformedLinesAreSkippedWithWarning() throws Exception {
        Files.writeString(dir.resolve(ClinicDataManager.MEDICINE_FILE),
                "id,name,quantity,price\nM001,Paracetamol,10,0.20\nM002,Broken,abc,1.00\nM003,Short\n",
                StandardCharsets.UTF_8);
        ClinicDataManager store = new ClinicDataManager(dir);
        List<Medicine> meds = store.loadMedicine();
        assertEquals(1, meds.size());
        assertEquals(2, store.getWarnings().size());
        assertTrue(store.getWarnings().get(0).contains("line 3"));
    }

    @Test
    void legacyFilesWithoutHeaderStillLoad() throws Exception {
        // Format produced by the original coursework submission.
        Files.writeString(dir.resolve(ClinicDataManager.PATIENT_FILE),
                "P002,Faiz,01732323131,20,m,selesema;DEMAM\nP003,Hasurdeen,013322424,21,M,\n",
                StandardCharsets.UTF_8);
        List<Patient> list = new ClinicDataManager(dir).loadPatients();
        assertEquals(2, list.size());
        assertEquals("M", list.get(0).getGender());
        assertEquals(List.of("selesema", "DEMAM"), list.get(0).getMedicalHistory());
    }
}
