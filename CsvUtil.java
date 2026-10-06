package com.hananimed.persistence;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180-style CSV helpers. Fields containing commas, quotes or
 * line breaks are wrapped in double quotes, so free-text notes such as
 * "Fever, cough" no longer shift the columns of a row.
 */
public final class CsvUtil {
    private static final char LIST_SEPARATOR = ';';

    private CsvUtil() { }

    public static String toLine(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(quote(fields.get(i)));
        }
        return sb.toString();
    }

    static String quote(String field) {
        String f = field == null ? "" : field;
        if (f.contains(",") || f.contains("\"") || f.contains("\n") || f.contains("\r")) {
            return '"' + f.replace("\"", "\"\"") + '"';
        }
        return f;
    }

    /** Parses one CSV line. Throws if a quoted field is never closed. */
    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (inQuotes) throw new IllegalArgumentException("unterminated quoted field");
        fields.add(current.toString());
        return fields;
    }

    /** Joins a list into one field using ';', escaping any ';' or '\' inside items. */
    public static String joinList(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(LIST_SEPARATOR);
            sb.append(items.get(i).replace("\\", "\\\\").replace(";", "\\;"));
        }
        return sb.toString();
    }

    /** Inverse of {@link #joinList(List)}. Plain ';'-separated legacy values also work. */
    public static List<String> splitList(String field) {
        List<String> items = new ArrayList<>();
        if (field == null || field.isEmpty()) return items;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (c == '\\' && i + 1 < field.length()) {
                current.append(field.charAt(++i));
            } else if (c == LIST_SEPARATOR) {
                items.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        items.add(current.toString());
        return items;
    }
}
