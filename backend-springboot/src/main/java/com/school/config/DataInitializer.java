package com.school.config;

import com.school.entity.AcademicRule;
import com.school.entity.LmdEnrollment;
import com.school.entity.Permission;
import com.school.entity.Role;
import com.school.entity.Setting;
import com.school.entity.User;
import com.school.entity.Level;
import com.school.enums.EducationCycle;
import com.school.repository.AcademicRuleRepository;
import com.school.repository.LevelRepository;
import com.school.repository.LmdEnrollmentRepository;
import com.school.repository.PermissionRepository;
import com.school.repository.RoleRepository;
import com.school.repository.SettingRepository;
import com.school.repository.StudentRepository;
import com.school.repository.UserRepository;
import com.school.service.SettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Initialise les données de référence (rôles, permissions, compte administrateur).
 * <p>
 * Le synchroniseur est idempotent et strictement additif : il crée les rôles et
 * permissions manquants et complète les rattachements, sans jamais supprimer
 * de données existantes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final SettingRepository settingRepository;
    private final SettingService settingService;
    private final PasswordEncoder passwordEncoder;
    private final LevelRepository levelRepository;
    private final AcademicRuleRepository academicRuleRepository;
    private final LmdEnrollmentRepository lmdEnrollmentRepository;
    private final StudentRepository studentRepository;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:Admin@123}")
    private String adminPassword;

    /** Toutes les permissions du système. */
    private static final List<String> ALL_PERMISSIONS = List.of(
            "STUDENT_READ", "STUDENT_WRITE", "STUDENT_DELETE",
            "TEACHER_READ", "TEACHER_WRITE",
            "CLASS_READ", "CLASS_WRITE",
            "SUBJECT_READ", "SUBJECT_WRITE",
            "GRADE_READ", "GRADE_WRITE",
            "PAYMENT_READ", "PAYMENT_WRITE",
            "ATTENDANCE_READ", "ATTENDANCE_WRITE",
            "EXAM_READ", "EXAM_WRITE",
            "SCHEDULE_READ", "SCHEDULE_WRITE",
            "REPORT_READ",
            "USER_READ", "USER_WRITE",
            "FINANCE_READ", "FINANCE_WRITE",
            "LIBRARY_READ", "LIBRARY_WRITE",
            "HR_READ", "HR_WRITE",
            "HOURS_READ", "HOURS_WRITE", "HOURS_PAY",
            "COMMUNICATION_READ", "COMMUNICATION_WRITE",
            "ACADEMIC_YEAR_READ", "ACADEMIC_YEAR_WRITE",
            "LMD_READ", "LMD_WRITE",
            "BACKUP_WRITE");

    /** Rattachements rôles → permissions (additifs par rapport au seed SQL). */
    private static final Map<String, Set<String>> ROLE_PERMISSIONS = new HashMap<>();

    static {
        ROLE_PERMISSIONS.put("SUPER_ADMIN", new HashSet<>(ALL_PERMISSIONS));
        ROLE_PERMISSIONS.put("DIRECTEUR", Set.of(
                "STUDENT_READ", "STUDENT_WRITE", "TEACHER_READ", "TEACHER_WRITE",
                "CLASS_READ", "CLASS_WRITE", "SUBJECT_READ", "SUBJECT_WRITE",
                "GRADE_READ", "PAYMENT_READ", "ATTENDANCE_READ", "EXAM_READ", "EXAM_WRITE",
                "SCHEDULE_READ", "SCHEDULE_WRITE", "REPORT_READ", "FINANCE_READ",
                "LIBRARY_READ", "HR_READ", "USER_READ", "COMMUNICATION_READ", "COMMUNICATION_WRITE",
                "ACADEMIC_YEAR_READ", "LMD_READ", "LMD_WRITE",
                "HOURS_READ", "HOURS_WRITE", "HOURS_PAY"));
        ROLE_PERMISSIONS.put("COMPTABLE", Set.of(
                "PAYMENT_READ", "PAYMENT_WRITE", "FINANCE_READ", "FINANCE_WRITE",
                "REPORT_READ", "STUDENT_READ", "COMMUNICATION_READ",
                "HOURS_READ", "HOURS_WRITE", "HOURS_PAY"));
        ROLE_PERMISSIONS.put("SECRETAIRE", Set.of(
                "STUDENT_READ", "STUDENT_WRITE", "TEACHER_READ", "CLASS_READ",
                "ATTENDANCE_READ", "ATTENDANCE_WRITE", "PAYMENT_READ", "EXAM_READ",
                "SCHEDULE_READ", "LIBRARY_READ", "LIBRARY_WRITE", "REPORT_READ",
                "COMMUNICATION_READ", "COMMUNICATION_WRITE", "ACADEMIC_YEAR_READ",
                "LMD_READ", "LMD_WRITE",
                "HOURS_READ", "HOURS_WRITE"));
        ROLE_PERMISSIONS.put("ENSEIGNANT", Set.of(
                "GRADE_READ", "GRADE_WRITE", "ATTENDANCE_READ", "ATTENDANCE_WRITE",
                "EXAM_READ", "SCHEDULE_READ", "STUDENT_READ", "SUBJECT_READ",
                "COMMUNICATION_READ", "COMMUNICATION_WRITE", "LMD_READ", "LMD_WRITE",
                "HOURS_READ"));
        ROLE_PERMISSIONS.put("PARENT", Set.of(
                "STUDENT_READ", "GRADE_READ", "ATTENDANCE_READ", "PAYMENT_READ",
                "SCHEDULE_READ", "COMMUNICATION_READ"));
        ROLE_PERMISSIONS.put("ELEVE", Set.of(
                "GRADE_READ", "ATTENDANCE_READ", "PAYMENT_READ", "SCHEDULE_READ",
                "COMMUNICATION_READ"));
        // Étudiant universitaire : accès LMD en lecture + son propre espace
        ROLE_PERMISSIONS.put("ETUDIANT", Set.of(
                "LMD_READ", "STUDENT_READ", "GRADE_READ", "ATTENDANCE_READ",
                "PAYMENT_READ", "SCHEDULE_READ", "COMMUNICATION_READ"));
    }

    @Override
    public void run(String... args) {
        syncRolesAndPermissions();
        initializeAdmin();
        seedSettings();
        seedLevelCycles();
        seedAcademicRules();
        migrateEtudiantRole();
    }

    /**
     * Migration idempotente : tout utilisateur lié à un étudiant ayant au moins
     * une inscription LMD reçoit le rôle ETUDIANT. Ne retire jamais ELEVE.
     */
    private void migrateEtudiantRole() {
        List<com.school.entity.LmdEnrollment> enrollments = lmdEnrollmentRepository.findAll();
        Set<Long> studentIds = enrollments.stream()
                .map(e -> e.getStudent().getId())
                .collect(Collectors.toSet());
        Optional<Role> etudiantRole = roleRepository.findByName("ETUDIANT");
        if (etudiantRole.isEmpty()) {
            return;
        }
        int migrated = 0;
        for (Long studentId : studentIds) {
            com.school.entity.Student student = studentRepository.findById(studentId).orElse(null);
            if (student == null || student.getUser() == null) continue;
            User user = student.getUser();
            if (user.getRoles().stream().noneMatch(r -> r.getName().equals("ETUDIANT"))) {
                user.getRoles().add(etudiantRole.get());
                userRepository.save(user);
                migrated++;
            }
        }
        if (migrated > 0) {
            log.info("Rôle ETUDIANT attribué à {} compte(s) universitaire(s)", migrated);
        }
    }

    /**
     * Règles académiques par défaut (cycle UNIVERSITE) si absentes.
     * Idempotent : les règles existantes ne sont pas écrasées.
     */
    private void seedAcademicRules() {
        Map<String, String> defaults = Map.ofEntries(
                Map.entry("validation_threshold", "10"),
                Map.entry("compensation_enabled", "true"),
                Map.entry("mention_passable", "10"),
                Map.entry("mention_assez_bien", "12"),
                Map.entry("mention_bien", "14"),
                Map.entry("mention_tres_bien", "16"),
                Map.entry("mention_excellent", "18"));
        int created = 0;
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            if (academicRuleRepository.findByCycleAndRuleKey("UNIVERSITE", entry.getKey()).isEmpty()) {
                academicRuleRepository.save(AcademicRule.builder()
                        .cycle("UNIVERSITE")
                        .ruleKey(entry.getKey())
                        .ruleValue(entry.getValue())
                        .description("Règle LMD : " + entry.getKey())
                        .build());
                created++;
            }
        }
        if (created > 0) {
            log.info("Règles académiques par défaut créées : {}", created);
        }
    }

    /**
     * Associe un cycle d'enseignement (EducationCycle) aux niveaux existants
     * sans cycle et crée les niveaux préscolaires / primaires manquants.
     * Idempotent et rétrocompatible : les niveaux déjà renseignés ne sont pas
     * modifiés.
     */
    private void seedLevelCycles() {
        Map<String, String[]> defaultCycles = Map.ofEntries(
                Map.entry("PS", new String[]{"Petite Section", "JARDIN"}),
                Map.entry("MS", new String[]{"Moyenne Section", "JARDIN"}),
                Map.entry("GS", new String[]{"Grande Section", "JARDIN"}),
                Map.entry("CP", new String[]{"CP", "PRIMAIRE"}),
                Map.entry("CE1", new String[]{"CE1", "PRIMAIRE"}),
                Map.entry("CE2", new String[]{"CE2", "PRIMAIRE"}),
                Map.entry("CM1", new String[]{"CM1", "PRIMAIRE"}),
                Map.entry("CM2", new String[]{"CM2", "PRIMAIRE"}),
                Map.entry("6E", new String[]{"6ème", "COLLEGE"}),
                Map.entry("5E", new String[]{"5ème", "COLLEGE"}),
                Map.entry("4E", new String[]{"4ème", "COLLEGE"}),
                Map.entry("3E", new String[]{"3ème", "COLLEGE"}),
                Map.entry("2ND", new String[]{"2nde", "LYCEE"}),
                Map.entry("1RE", new String[]{"1ère", "LYCEE"}),
                Map.entry("TLE", new String[]{"Terminale", "LYCEE"}));

        int updated = 0;
        int created = 0;
        for (Map.Entry<String, String[]> entry : defaultCycles.entrySet()) {
            String code = entry.getKey();
            String name = entry.getValue()[0];
            EducationCycle cycle = EducationCycle.valueOf(entry.getValue()[1]);
            Optional<Level> existing = levelRepository.findByCode(code);
            if (existing.isPresent()) {
                Level level = existing.get();
                if (level.getEducationCycle() == null) {
                    level.setEducationCycle(cycle);
                    levelRepository.save(level);
                    updated++;
                }
            } else {
                levelRepository.save(Level.builder()
                        .name(name)
                        .code(code)
                        .educationCycle(cycle)
                        .build());
                created++;
            }
        }
        if (updated > 0 || created > 0) {
            log.info("Cycles d'enseignement : {} niveau(x) mis à jour, {} niveau(x) créé(s)",
                    updated, created);
        }
    }

    private void seedSettings() {
        Map<String, String> defaults = settingService.defaults();
        defaults.forEach((key, value) -> {
            if (settingRepository.findById(key).isEmpty()) {
                settingRepository.save(Setting.builder()
                        .key(key)
                        .value(value)
                        .description(descriptionOf(key))
                        .build());
            }
        });
    }

    private String descriptionOf(String key) {
        return switch (key) {
            case "SCHOOL_NAME" -> "Nom de l'établissement";
            case "SCHOOL_ADDRESS" -> "Adresse de l'établissement";
            case "SCHOOL_PHONE" -> "Téléphone de l'établissement";
            case "SCHOOL_EMAIL" -> "Email de l'établissement";
            case "RECEIPT_FOOTER" -> "Pied de page des reçus de paiement";
            case "WHATSAPP_ENABLED" -> "Activer l'envoi WhatsApp";
            case "WHATSAPP_DEFAULT_NUMBER" -> "Indicatif par défaut (ex. 237)";
            case "ACTIVE_CYCLES" -> "Cycles d'enseignement activés (JSON)";
            default -> key;
        };
    }

    private void syncRolesAndPermissions() {
        Map<String, Permission> permissionsByName = new HashMap<>();
        for (String name : ALL_PERMISSIONS) {
            Permission permission = permissionRepository.findByName(name)
                    .orElseGet(() -> permissionRepository.save(Permission.builder()
                            .name(name)
                            .description(name)
                            .build()));
            permissionsByName.put(name, permission);
        }

        int[] created = {0};
        for (Map.Entry<String, Set<String>> entry : ROLE_PERMISSIONS.entrySet()) {
            Role role = roleRepository.findByName(entry.getKey())
                    .orElseGet(() -> {
                        created[0]++;
                        return roleRepository.save(Role.builder()
                                .name(entry.getKey())
                                .description(entry.getKey())
                                .build());
                    });
            Set<String> missing = entry.getValue().stream()
                    .filter(name -> role.getPermissions().stream()
                            .noneMatch(p -> p.getName().equals(name)))
                    .collect(Collectors.toSet());
            if (!missing.isEmpty()) {
                missing.forEach(name -> role.getPermissions().add(permissionsByName.get(name)));
                roleRepository.save(role);
                log.info("Rôles synchronisés : {} += {}", role.getName(), missing);
            }
        }
        if (created[0] > 0) {
            log.info("Rôles créés automatiquement : {}", created[0]);
        }
    }

    private void initializeAdmin() {
        if (userRepository.existsByUsername(adminUsername)) {
            return;
        }
        Optional<Role> superAdminRole = roleRepository.findByName("SUPER_ADMIN");
        if (superAdminRole.isEmpty()) {
            log.warn("Rôle SUPER_ADMIN introuvable après synchronisation.");
            return;
        }
        User admin = User.builder()
                .username(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .email("admin@school.edu")
                .firstName("Super")
                .lastName("Admin")
                .phone("+237600000000")
                .enabled(true)
                .build();
        admin.getRoles().add(superAdminRole.get());
        userRepository.save(admin);
        log.info("Compte administrateur créé : {}", adminUsername);
    }
}