package com.hananimed.persistence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvUtilTest {

    @Test
    void plainFieldsAreNotQuoted() {
        assertEquals("P001,Aisyah,34", CsvUtil.toLine(List.of("P001", "Aisyah", "34")));
    }

    @Test
    void fieldsWithCommasAndQuotesRoundTrip() {
        List<String> fields = List.of("A001", "Fever, cough", "He said \"rest\"", "");
        String line = CsvUtil.toLine(fields);
        assertEquals("A001,\"Fever, cough\",\"He said \"\"rest\"\"\",", line);
        assertEquals(fields, CsvUtil.parseLine(line));
    }

    @Test
    void trailingEmptyFieldIsKept() {
        assertEquals(List.of("P003", "Kavitha", ""), CsvUtil.parseLine("P003,Kavitha,"));
    }

    @Test
    void unterminatedQuoteIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CsvUtil.parseLine("P001,\"broken"));
    }

    @Test
    void listItemsContainingSeparatorRoundTrip() {
        List<String> items = List.of("Fever; mild", "Back\\slash", "Prescribed: X (Qty: 2)");
        assertEquals(items, CsvUtil.splitList(CsvUtil.joinList(items)));
    }

    @Test
    void legacySemicolonListStillParses() {
        assertEquals(List.of("selesema", "DEMAM"), CsvUtil.splitList("selesema;DEMAM"));
        assertEquals(List.of(), CsvUtil.splitList(""));
    }
}
