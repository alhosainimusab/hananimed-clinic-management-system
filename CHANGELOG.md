# Changelog

## 1.1.0: portfolio version (after course submission)

These changes were made by Musaab Fahmi Fadhl Al-Husaini after the BIC20904 submission. Every bug listed below was reproduced against the submitted code before it was fixed.

### Bug fixes
- **Update Medicine did nothing.** It printed "Name updated" and "Price updated" but never changed the record, because `Medicine` had no setters. It now updates and saves.
- **Negative prescription quantity increased stock.** Prescribing `-50` added 50 units. Quantities must now be between 1 and the available stock.
- **Commas in free text corrupted the CSV.** A clinical note such as `Fever, cough` shifted the columns, and part of the note was lost on reload. Fields are now quoted (RFC 4180), and `;` inside history items is escaped.
- **Non-numeric input crashed the program.** Entering `abc` for a quantity or price threw `NumberFormatException` in Add Medicine, Restock and Update Medicine. All numeric input is now re-prompted.
- **Duplicate medicine IDs were allowed**, which made later lookups ambiguous.
- **Removing a non-existent patient or staff member reported success.** It now reports "not found".
- **Doctor menu header showed `DR. DR. HANANI`.** The code added "Dr." in front of names that already contained it.
- **A malformed line in `medicine.csv` crashed start-up.** Bad lines in any file are now skipped with a warning.
- **Ages and restock amounts could be negative.**

### Features described in the report, now implemented
- **Doctor double-booking prevention.** Bookings closer than 30 minutes for the same doctor are rejected (report test case SYS_TC02, *Schedule Appointment* activity diagram).
- **Past-date rejection** for new and rescheduled appointments (report test case ERR_TC05).
- **Doctor schedule view**, "View My Appointments" (report test case DOC_TC02).
- **Confirmation step before deleting** patients, staff, medicine and appointments (report test steps ADMIN_PM_TC04, ADMIN_AM_TC03).

### New
- Low-Stock Medicines and Patient Demographics reports.
- Update staff email and role. A doctor who still has appointments cannot be changed to receptionist.
- Input validation for phone numbers, email and staff role; IDs are normalised to upper case.
- "Invalid selection" message shown in every menu, not just two of them.

### Code quality
- Business rules moved out of the 712-line `ClinicSystem` into `ClinicService`. The console UI now only reads input and prints output.
- Classes organised into Maven packages (`model`, `persistence`, `service`, `ui`).
- Four copy-pasted load/save method pairs replaced with one generic loader and one generic saver.
- 53 JUnit 5 tests, including scripted end-to-end console sessions.
- CSV files have header rows, UTF-8 encoding and atomic saves (write to a temp file, then move). Headerless files in the old format still load.
- The data folder is configurable, and the program exits cleanly when input ends.
- Status markers changed from emoji to `[OK]` / `[ERROR]`, because emoji showed as `?` in the Windows console.
- Comments translated to English; the original code mixed Malay and English.
- Sample data replaced with fictional records.
- IntelliJ workspace files removed; Maven build and GitHub Actions CI added.

## 1.0.0: course submission (Group 13, January 2026)
The original group submission for BIC20904 Object-Oriented Programming at UTHM.
