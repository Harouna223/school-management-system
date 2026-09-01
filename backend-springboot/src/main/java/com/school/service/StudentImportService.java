package com.school.service;

import com.school.dto.request.StudentRequest;
import com.school.dto.response.StudentImportResult;
import com.school.enums.Gender;
import com.school.repository.SchoolClassRepository;
import com.school.repository.StudentRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Import Excel des élèves : lecture, validation, détection des doublons,
 * import en base et rapport détaillé. Aucune ligne invalide n'est importée
 * silencieusement.
 */
@Service
@RequiredArgsConstructor
public class StudentImportService {

    private static final Logger log = LoggerFactory.getLogger(StudentImportService.class);

    private final StudentRepository studentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentService studentService;

    private static final String[] HEADERS = {
            "Prénom", "Nom", "Genre", "Date de naissance", "Classe",
            "Téléphone", "Email", "Adresse",
            "Parent prénom", "Parent nom", "Parent téléphone", "Parent email", "Parent profession"
    };

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    };

    /**
     * Génère le modèle de fichier à remplir.
     */
    public byte[] template() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Eleves");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("Jean");
            example.createCell(1).setCellValue("Martin");
            example.createCell(2).setCellValue("M");
            example.createCell(3).setCellValue("2012-05-14");
            example.createCell(4).setCellValue("6e A");
            example.createCell(5).setCellValue("690123456");
            example.createCell(6).setCellValue("jean.martin@example.com");
            example.createCell(7).setCellValue("Douala");
            example.createCell(8).setCellValue("Marie");
            example.createCell(9).setCellValue("Martin");
            example.createCell(10).setCellValue("690123457");
            example.createCell(11).setCellValue("marie.martin@example.com");
            example.createCell(12).setCellValue("Mère");
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        }
    }

    /**
     * Importe un fichier Excel : valide chaque ligne, détecte les doublons
     * (email ou identité + date de naissance, en base ou dans le fichier),
     * importe les lignes valides et retourne un rapport détaillé.
     */
    @Transactional
    public StudentImportResult importFromExcel(MultipartFile file, HttpServletRequest httpRequest) {
        List<StudentImportResult.RowError> errors = new ArrayList<>();
        int total = 0;
        int imported = 0;

        Set<String> fileEmails = new HashSet<>();
        Set<String> fileIdentities = new HashSet<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue; // ligne d'en-tête
                }
                total++;

                List<String> messages = new ArrayList<>();
                String firstName = cellText(row, 0);
                String lastName = cellText(row, 1);
                String genderRaw = cellText(row, 2);
                String birthRaw = cellText(row, 3);
                String classRaw = cellText(row, 4);
                String phone = cellText(row, 5);
                String email = cellText(row, 6);
                String address = cellText(row, 7);
                String parentFirstName = cellText(row, 8);
                String parentLastName = cellText(row, 9);
                String parentPhone = cellText(row, 10);
                String parentEmail = cellText(row, 11);
                String parentProfession = cellText(row, 12);

                if (firstName.isBlank() && lastName.isBlank()) {
                    continue; // ligne vide
                }
                if (firstName.isBlank()) {
                    messages.add("Prénom manquant");
                }
                if (lastName.isBlank()) {
                    messages.add("Nom manquant");
                }

                Gender gender = parseGender(genderRaw, messages);
                LocalDate birthDate = parseDate(birthRaw, messages);
                Long classId = findClassId(classRaw, messages);

                if (email != null && !email.isBlank() && !isEmailValid(email)) {
                    messages.add("Email invalide : " + email);
                }

                // Doublons dans le fichier
                String emailKey = email != null ? email.trim().toLowerCase() : null;
                if (emailKey != null && !fileEmails.add(emailKey)) {
                    messages.add("Email en double dans le fichier");
                }
                String identityKey = firstName + "|" + lastName + "|" + birthDate;
                if (birthDate != null && !fileIdentities.add(identityKey)) {
                    messages.add("Élève en double dans le fichier (même nom et date de naissance)");
                }

                // Doublons en base
                if (emailKey != null && studentRepository.existsByEmail(emailKey)) {
                    messages.add("Un élève avec cet email existe déjà");
                }
                if (birthDate != null && studentRepository.existsByFirstNameAndLastNameAndBirthDate(
                        firstName, lastName, birthDate)) {
                    messages.add("Élève déjà inscrit (même nom et date de naissance)");
                }

                if (!messages.isEmpty()) {
                    errors.add(StudentImportResult.RowError.builder()
                            .row(row.getRowNum() + 1)
                            .message(String.join(" ; ", messages))
                            .build());
                    continue;
                }

                try {
                    StudentRequest request = StudentRequest.builder()
                            .firstName(firstName)
                            .lastName(lastName)
                            .gender(gender)
                            .birthDate(birthDate)
                            .classId(classId)
                            .phone(phone.isEmpty() ? null : phone)
                            .email(email == null || email.isEmpty() ? null : email)
                            .address(address.isEmpty() ? null : address)
                            .parentFirstName(parentFirstName.isEmpty() ? null : parentFirstName)
                            .parentLastName(parentLastName.isEmpty() ? null : parentLastName)
                            .parentPhone(parentPhone.isEmpty() ? null : parentPhone)
                            .parentEmail(parentEmail.isEmpty() ? null : parentEmail)
                            .parentProfession(parentProfession.isEmpty() ? null : parentProfession)
                            .createUserAccount(false)
                            .build();
                    studentService.create(request, httpRequest);
                    imported++;
                } catch (Exception e) {
                    log.warn("Élève non importé (ligne {}) : {}", row.getRowNum() + 1, e.getMessage());
                    errors.add(StudentImportResult.RowError.builder()
                            .row(row.getRowNum() + 1)
                            .message("Erreur d'import : " + e.getMessage())
                            .build());
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Fichier rejeté à l'ouverture : {}", e.getMessage());
            throw new com.school.exception.BusinessException(
                    "Fichier illisible : vérifiez qu'il s'agit d'un fichier Excel (.xlsx)");
        }

        int skipped = errors.size();
        return StudentImportResult.builder()
                .totalRows(total)
                .imported(imported)
                .skipped(skipped)
                .errors(errors)
                .build();
    }

    private String cellText(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case NUMERIC:
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double value = cell.getNumericCellValue();
                if (value == Math.floor(value) && !Double.isInfinite(value)) {
                    return String.valueOf((long) value);
                }
                return String.valueOf(value);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getRichStringCellValue().getString().trim();
                } catch (Exception ignored) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case STRING:
                return cell.getStringCellValue().trim();
            default:
                return "";
        }
    }

    private Gender parseGender(String raw, List<String> messages) {
        String value = raw.trim().toUpperCase();
        Set<String> males = Set.of("M", "MASC", "MASCULIN", "MALE", "HOMME", "GARCON", "GARÇON", "H");
        Set<String> females = Set.of("F", "FEM", "FEMININ", "FÉMININ", "FEMALE", "FEMME", "FILLE");
        if (males.contains(value)) {
            return Gender.MALE;
        }
        if (females.contains(value)) {
            return Gender.FEMALE;
        }
        messages.add("Genre invalide : " + (raw.isBlank() ? "manquant" : raw));
        return null;
    }

    private LocalDate parseDate(String raw, List<String> messages) {
        if (raw.isBlank()) {
            return null;
        }
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw.trim(), fmt);
            } catch (DateTimeParseException ignored) {
                // essai du format suivant
            }
        }
        messages.add("Date de naissance invalide : " + raw);
        return null;
    }

    private Long findClassId(String raw, List<String> messages) {
        if (raw.isBlank()) {
            messages.add("Classe manquante");
            return null;
        }
        String value = raw.trim();
        return schoolClassRepository.findFirstByNameIgnoreCase(value)
                .or(() -> schoolClassRepository.findFirstByCodeIgnoreCase(value))
                .map(c -> c.getId())
                .orElseGet(() -> {
                    messages.add("Classe introuvable : " + value);
                    return null;
                });
    }

    private boolean isEmailValid(String email) {
        return email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }
}