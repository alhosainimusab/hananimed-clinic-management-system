package com.hananimed.service;

import com.hananimed.model.Medicine;
import com.hananimed.persistence.ClinicDataManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClinicServiceTest {
    /** "Now" for every test: 1 Jan 2027, 08:00. */
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2027, 1, 1, 8, 0).toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
    private static final LocalDateTime MON_9AM = LocalDateTime.of(2027, 1, 11, 9, 0);

    @TempDir
    Path dir;
    ClinicService service;

    @BeforeEach
    void setUp() throws Exception {
        service = newService();
        service.registerPatient("P001", "Aisyah Rahman", "0120000001", 34, "F");
        service.registerPatient("P002", "Daniel Lee", "012-000 0002", 27, "m");
        service.addStaff("S001", "Dr. Hanani", "0130000001", "doctor", "hanani@clinic.example");
        service.addStaff("S002", "Dr. Lim", "0130000002", "Doctor", "lim@clinic.example");
        service.addStaff("S004", "Farah Aziz", "0130000004", "Receptionist", "desk@clinic.example");
        service.addMedicine("M001", "Paracetamol 500 mg", 100, 0.20);
        service.addMedicine("M003", "Amoxicillin 250 mg", 40, 0.80);
        service.createAppointment("A001", "P001", "S001", MON_9AM, "Fever");
    }

    private ClinicService newService() throws Exception {
        return new ClinicService(new ClinicDataManager(dir), CLOCK);
    }

    @Nested
    class Patients {
        @Test
        void inputIsNormalised() {
            var p = service.findPatient("p002").orElseThrow();
            assertEquals("0120000002", p.getPhone());
            assertEquals("M", p.getGender());
        }

        @Test
        void idsAreStoredUpperCase() {
            service.registerPatient("p011", "Adam", "0120000011", 21, "M");
            assertTrue(service.getPatients().stream().anyMatch(p -> p.getId().equals("P011")));
        }

        @Test
        void duplicateIdIsRejectedCaseInsensitively() {
            var e = assertThrows(ClinicException.class,
                    () -> service.registerPatient("p001", "X", "0120000009", 20, "M"));
            assertTrue(e.getMessage().contains("already exists"));
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 131})
        void unrealisticAgeIsRejected(int age) {
            assertThrows(ClinicException.class, () -> service.registerPatient("P009", "X", "0120000009", age, "M"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "abc", "123", "01200000012345"})
        void invalidPhoneIsRejected(String phone) {
            assertThrows(ClinicException.class, () -> service.registerPatient("P009", "X", phone, 20, "M"));
        }

        @Test
        void invalidGenderIsRejected() {
            assertThrows(ClinicException.class, () -> service.registerPatient("P009", "X", "0120000009", 20, "X"));
        }

        @Test
        void removingPatientCascadesToAppointments() {
            assertEquals(1, service.removePatient("P001"));
            assertTrue(service.getAppointments().isEmpty());
        }

        @Test
        void removingUnknownPatientFails() {
            assertThrows(ClinicException.class, () -> service.removePatient("P999"));
        }
    }

    @Nested
    class Appointments {
        @Test
        void doubleBookingWithinSlotIsRejected() {
            var e = assertThrows(ClinicException.class,
                    () -> service.createAppointment("A002", "P002", "S001", MON_9AM.plusMinutes(29), ""));
            assertTrue(e.getMessage().startsWith("Doctor unavailable"));
        }

        @Test
        void bookingExactlyOneSlotLaterIsAllowed() {
            service.createAppointment("A002", "P002", "S001", MON_9AM.plusMinutes(ClinicService.SLOT_MINUTES), "");
            assertEquals(2, service.getAppointments().size());
        }

        @Test
        void otherDoctorSameTimeIsAllowed() {
            service.createAppointment("A002", "P002", "S002", MON_9AM, "");
            assertEquals(2, service.getAppointments().size());
        }

        @Test
        void pastDateIsRejected() {
            var e = assertThrows(ClinicException.class,
                    () -> service.createAppointment("A002", "P002", "S002", LocalDateTime.of(2026, 12, 31, 9, 0), ""));
            assertEquals("Cannot schedule in the past.", e.getMessage());
        }

        @Test
        void receptionistCannotBeBookedAsDoctor() {
            assertThrows(ClinicException.class,
                    () -> service.createAppointment("A002", "P002", "S004", MON_9AM.plusDays(1), ""));
        }

        @Test
        void unknownPatientIsRejected() {
            assertThrows(ClinicException.class,
                    () -> service.createAppointment("A002", "P999", "S001", MON_9AM.plusDays(1), ""));
        }

        @Test
        void reschedulingDoesNotClashWithItself() {
            service.updateAppointmentDateTime("A001", MON_9AM.plusMinutes(10));
            assertEquals(MON_9AM.plusMinutes(10), service.findAppointment("A001").orElseThrow().getDateTime());
        }

        @Test
        void reassigningToBusyDoctorIsRejected() {
            service.createAppointment("A002", "P002", "S002", MON_9AM, "");
            assertThrows(ClinicException.class, () -> service.updateAppointmentDoctor("A002", "S001"));
        }

        @Test
        void removingDoctorCascadesToAppointments() {
            assertEquals(1, service.removeStaff("S001"));
            assertTrue(service.getAppointments().isEmpty());
        }

        @Test
        void doctorWithAppointmentsCannotBecomeReceptionist() {
            assertThrows(ClinicException.class, () -> service.updateStaffRole("S001", "Receptionist"));
        }
    }

    @Nested
    class Inventory {
        @Test
        void updateMedicineActuallyChangesAndPersists() throws Exception {
            service.updateMedicineName("M001", "Ibuprofen 200 mg");
            service.updateMedicinePrice("M001", 0.45);
            Medicine reloaded = newService().findMedicine("M001").orElseThrow();
            assertEquals("Ibuprofen 200 mg", reloaded.getName());
            assertEquals(0.45, reloaded.getPrice(), 1e-9);
        }

        @Test
        void duplicateMedicineIdIsRejected() {
            assertThrows(ClinicException.class, () -> service.addMedicine("m001", "Dup", 1, 1));
        }

        @Test
        void restockMustBePositive() {
            assertThrows(ClinicException.class, () -> service.restock("M001", 0));
            service.restock("M001", 25);
            assertEquals(125, service.findMedicine("M001").orElseThrow().getQuantity());
        }

        @Test
        void negativePriceIsRejected() {
            assertThrows(ClinicException.class, () -> service.updateMedicinePrice("M001", -1));
        }
    }

    @Nested
    class DoctorActions {
        @Test
        void prescribingReducesStockAndRecordsHistory() {
            service.prescribe("S001", "P001", "M001", 10);
            assertEquals(90, service.findMedicine("M001").orElseThrow().getQuantity());
            List<String> history = service.findPatient("P001").orElseThrow().getMedicalHistory();
            assertEquals("Prescribed: Paracetamol 500 mg (Qty: 10)", history.get(history.size() - 1));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -50, 101})
        void invalidPrescriptionQuantityDoesNotChangeStock(int qty) {
            assertThrows(ClinicException.class, () -> service.prescribe("S001", "P001", "M001", qty));
            assertEquals(100, service.findMedicine("M001").orElseThrow().getQuantity());
        }

        @Test
        void unassignedDoctorCannotPrescribeOrAddNotes() {
            assertThrows(ClinicException.class, () -> service.prescribe("S002", "P001", "M001", 1));
            assertThrows(ClinicException.class, () -> service.addClinicalNote("S002", "P001", "note"));
        }

        @Test
        void clinicalNoteWithCommaAndSemicolonSurvivesReload() throws Exception {
            service.addClinicalNote("S001", "P001", "Fever, cough; mild");
            List<String> history = newService().findPatient("P001").orElseThrow().getMedicalHistory();
            assertEquals(List.of("Fever, cough; mild"), history);
        }

        @Test
        void assignedPatientsAreDerivedFromAppointments() {
            assertTrue(service.assignedPatientIds("S001").contains("P001"));
            assertFalse(service.isAssigned("S001", "P002"));
        }
    }

    @Nested
    class Reports {
        @Test
        void appointmentsPerDoctorIncludesZeroCounts() {
            Map<String, Long> byName = new java.util.LinkedHashMap<>();
            service.appointmentsPerDoctor().forEach((d, c) -> byName.put(d.getName(), c));
            assertEquals(Map.of("Dr. Hanani", 1L, "Dr. Lim", 0L), byName);
        }

        @Test
        void lowStockListsOnlyItemsBelowThreshold() {
            assertEquals(List.of("M003"), service.lowStockMedicines().stream().map(Medicine::getId).toList());
        }

        @Test
        void demographics() {
            assertEquals(2, service.totalPatients());
            assertEquals(30.5, service.averagePatientAge(), 1e-9);
            assertEquals(Map.of("F", 1L, "M", 1L), service.patientsByGender());
            assertEquals(2L, service.patientsByAgeGroup().get("18-39"));
        }

        @Test
        void ageGroupBoundaries() {
            assertEquals("0-17", ClinicService.ageGroup(17));
            assertEquals("18-39", ClinicService.ageGroup(18));
            assertEquals("40-59", ClinicService.ageGroup(40));
            assertEquals("60+", ClinicService.ageGroup(60));
        }
    }

    @Test
    void stateSurvivesRestart() throws Exception {
        ClinicService reloaded = newService();
        assertEquals(2, reloaded.getPatients().size());
        assertEquals(3, reloaded.getStaff().size());
        assertEquals(1, reloaded.getAppointments().size());
        assertEquals(2, reloaded.getInventory().size());
    }
}
