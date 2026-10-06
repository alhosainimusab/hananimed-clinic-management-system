package com.hananimed.ui;

import com.hananimed.persistence.ClinicDataManager;
import com.hananimed.service.ClinicService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end tests that drive the real console menus with scripted input. */
class ClinicSystemTest {

    @TempDir
    Path dir;

    private String runSession(String input) throws Exception {
        ClinicService service = new ClinicService(new ClinicDataManager(dir), Clock.systemDefaultZone());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        new ClinicSystem(service, new Scanner(input), out).run();
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void receptionistRegistersPatientAndItIsSaved() throws Exception {
        String output = runSession(String.join("\n",
                "3", "1",                       // receptionist -> register
                "P001", "", "Aisyah Rahman",     // empty name is re-prompted
                "0120000001", "abc", "34",       // non-numeric age is re-prompted
                "x", "F", "Y",
                "0", "0") + "\n");
        assertTrue(output.contains("[ERROR] Name cannot be empty."));
        assertTrue(output.contains("[ERROR] Enter a valid number."));
        assertTrue(output.contains("[ERROR] Enter M or F."));
        assertTrue(output.contains("[OK] Patient registered successfully."));

        ClinicService reloaded = new ClinicService(new ClinicDataManager(dir), Clock.systemDefaultZone());
        assertEquals("Aisyah Rahman", reloaded.findPatient("P001").orElseThrow().getName());
    }

    @Test
    void invalidMenuOptionShowsMessage() throws Exception {
        String output = runSession("99\n1\n42\n0\n0\n");
        assertEquals(2, output.split("Invalid selection\\.", -1).length - 1);
    }

    @Test
    void unknownDoctorIsDenied() throws Exception {
        assertTrue(runSession("2\nS999\n0\n").contains("Access Denied: Invalid Doctor ID."));
    }

    @Test
    void endOfInputExitsCleanly() throws Exception {
        assertTrue(runSession("1\n1\n").endsWith("System closed. Goodbye." + System.lineSeparator()));
    }
}
