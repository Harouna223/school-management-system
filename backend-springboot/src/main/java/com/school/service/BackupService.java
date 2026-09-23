package com.school.service;

import com.school.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sauvegarde et restauration de la base de données.
 * <p>
 * La sauvegarde est un dump SQL portable (header de validation {@code -- SMS BACKUP}),
 * généré via JDBC. La restauration n'accepte que les fichiers produits par ce
 * service (garde-fou anti-destruction).
 */
@Service
@RequiredArgsConstructor
public class BackupService {

    public static final String BACKUP_MARKER = "-- SMS BACKUP";

    /** Taille maximale acceptée pour un fichier de sauvegarde (100 Mo). */
    private static final long MAX_BACKUP_SIZE_BYTES = 100L * 1024 * 1024;

    /** Nombre maximal d'instructions acceptées dans une sauvegarde (garde-fou anti-DoS). */
    private static final int MAX_STATEMENTS = 500_000;

    /**
     * Formes d'instructions autorisées à la restauration. Toute autre instruction
     * est refusée : c'est ce qui empêche l'exécution de SQL arbitraire (DROP DATABASE,
     * GRANT, CREATE USER, LOAD_FILE, …) à partir d'un fichier uploadé.
     */
    private static final Pattern SET_FOREIGN_KEY_CHECKS =
            Pattern.compile("(?is)^SET\\s+FOREIGN_KEY_CHECKS\\s*=\\s*[01]$");
    private static final Pattern DROP_TABLE =
            Pattern.compile("(?is)^DROP\\s+TABLE\\s+IF\\s+EXISTS\\s+`[A-Za-z0-9_$]+`$");
    private static final Pattern CREATE_TABLE_HEAD =
            Pattern.compile("(?is)^CREATE\\s+TABLE\\s+`[A-Za-z0-9_$]+`\\s*\\(");
    private static final Pattern INSERT_VALUES_HEAD =
            Pattern.compile("(?is)^INSERT\\s+INTO\\s+`[A-Za-z0-9_$]+`\\s+VALUES\\s*\\(");

    private final JdbcTemplate jdbcTemplate;

    /**
     * Génère un dump SQL complet de la base.
     */
    public byte[] exportBackup() {
        List<String> tables = jdbcTemplate.query(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name",
                (rs, rowNum) -> rs.getString(1));

        StringBuilder sb = new StringBuilder();
        sb.append(BACKUP_MARKER).append(" v1\n");
        sb.append("-- Généré le ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("\n");
        sb.append("SET FOREIGN_KEY_CHECKS = 0;\n\n");

        for (String table : tables) {
            sb.append("DROP TABLE IF EXISTS `").append(table).append("`;\n");
            sb.append("CREATE TABLE `").append(table).append("` (\n")
                    .append(createTableDefinition(table)).append("\n);\n");

            List<List<Object>> rows = jdbcTemplate.query(
                    "SELECT * FROM `" + table + "`", (rs, rowNum) -> readRow(rs));
            for (List<Object> row : rows) {
                sb.append(insertStatement(table, row)).append("\n");
            }
            sb.append("\n");
        }

        sb.append("SET FOREIGN_KEY_CHECKS = 1;\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String createTableDefinition(String table) {
        List<String> definitions = jdbcTemplate.query(
                "SHOW CREATE TABLE `" + table + "`",
                (rs, rowNum) -> rs.getString(2));
        if (definitions.isEmpty()) {
            throw new BusinessException("Impossible de lire la définition de la table " + table);
        }
        // « CREATE TABLE `x` (...) » → on retourne uniquement la partie entre parenthèses.
        // La parenthèse fermante est recherchée par appariement (et non par lastIndexOf)
        // pour ne pas tronquer une définition contenant « ) » dans un COMMENT.
        String ddl = definitions.get(0);
        int open = ddl.indexOf('(');
        if (open < 0) {
            throw new BusinessException("Définition illisible pour la table " + table);
        }
        int close = matchingParenthesis(ddl, open);
        return ddl.substring(open + 1, close);
    }

    /**
     * Index de la parenthèse fermant la parenthèse ouverte à {@code open},
     * en ignorant le contenu des littéraux et des identifiants entre accents graves.
     */
    private int matchingParenthesis(String ddl, int open) {
        int depth = 0;
        boolean inLiteral = false;
        boolean escaped = false;
        char quote = 0;
        for (int i = open; i < ddl.length(); i++) {
            char c = ddl.charAt(i);
            if (inLiteral) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    inLiteral = false;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                inLiteral = true;
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return ddl.length() - 1;
    }

    private List<Object> readRow(ResultSet rs) throws SQLException {
        int cols = rs.getMetaData().getColumnCount();
        List<Object> values = new ArrayList<>();
        for (int i = 1; i <= cols; i++) {
            Object value = rs.getObject(i);
            if (value instanceof byte[] bytes) {
                values.add(bytes);
            } else {
                values.add(value);
            }
        }
        return values;
    }

    private String insertStatement(String table, List<Object> row) {
        StringBuilder sb = new StringBuilder("INSERT INTO `").append(table).append("` VALUES (");
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(sqlValue(row.get(i)));
        }
        return sb.append(");").toString();
    }

    /**
     * Sérialise une valeur SQL de manière portable : dates et horodatages entre
     * guillemets, binaires en hexadécimal (0x…), booléens en 1/0, chaînes échappées.
     */
    private String sqlValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String s) {
            return sqlString(s);
        }
        if (value instanceof byte[] bytes) {
            return "0x" + bytesToHex(bytes);
        }
        if (value instanceof java.sql.Timestamp ts) {
            return "'" + ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "'";
        }
        if (value instanceof java.sql.Date d) {
            return "'" + d.toLocalDate() + "'";
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return "'" + ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "'";
        }
        if (value instanceof java.time.LocalDate ld) {
            return "'" + ld + "'";
        }
        if (value instanceof java.time.LocalTime lt) {
            return "'" + lt + "'";
        }
        if (value instanceof Boolean b) {
            return b ? "1" : "0";
        }
        return String.valueOf(value);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Sérialise une chaîne en littéral SQL sûr : les caractères de contrôle
     * (retour à la ligne, tabulation, NUL, fin de fichier) et l'antislash sont
     * échappés pour qu'une valeur ne puisse jamais casser la structure du dump
     * (une instruction par ligne, terminaison par {@code ;}).
     */
    private String sqlString(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2).append('\'');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\0' -> sb.append("\\0");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("''");
                case '"' -> sb.append("\\\"");
                case '\032' -> sb.append("\\Z");
                default -> sb.append(c);
            }
        }
        return sb.append('\'').toString();
    }

    /**
     * Restaure un dump SQL produit par {@link #exportBackup()}.
     * <p>
     * Refuse tout fichier ne commençant pas par le marqueur {@value #BACKUP_MARKER}
     * et n'exécute que les instructions produites par l'export (CREATE TABLE,
     * DROP TABLE IF EXISTS, INSERT INTO … VALUES, SET FOREIGN_KEY_CHECKS) : un
     * fichier piégé ne peut donc pas exécuter de SQL arbitraire.
     */
    @Transactional
    public int restoreBackup(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Aucun fichier de sauvegarde fourni.");
        }
        if (file.getSize() > MAX_BACKUP_SIZE_BYTES) {
            throw new BusinessException("Fichier trop volumineux : 100 Mo maximum.");
        }

        String content;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();
            if (firstLine == null || !firstLine.startsWith(BACKUP_MARKER)) {
                throw new BusinessException("Fichier non reconnu : utilisez une sauvegarde générée par l'application.");
            }
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            content = sb.toString();
        } catch (IOException ex) {
            throw new BusinessException("Impossible de lire le fichier de sauvegarde.");
        }

        List<String> statements = splitStatements(content);
        int executed = 0;
        try {
            for (String statement : statements) {
                assertStatementAllowed(statement);
                jdbcTemplate.execute(statement);
                executed++;
            }
        } finally {
            // La restauration désactive temporairement les contraintes de clés
            // étrangères : ne jamais laisser cet état sur une connexion du pool.
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
        return executed;
    }

    /**
     * Découpe le dump en instructions terminées par {@code ;}, en ignorant les
     * points-virgules situés dans un littéral ou un identifiant entre accents
     * graves (une valeur de donnée ne peut donc pas casser le découpage).
     */
    private List<String> splitStatements(String content) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inLiteral = false;
        boolean escaped = false;
        char quote = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inLiteral) {
                current.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    inLiteral = false;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                inLiteral = true;
                quote = c;
                current.append(c);
            } else if (c == ';') {
                current.append(c);
                addStatement(statements, current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            throw new BusinessException(
                    "Sauvegarde corrompue : la dernière instruction n'est pas terminée par « ; ».");
        }
        return statements;
    }

    private void addStatement(List<String> statements, String raw) {
        // Les commentaires « -- … » et les lignes vides ne sont pas des instructions.
        StringBuilder cleaned = new StringBuilder();
        for (String line : raw.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            cleaned.append(trimmed).append('\n');
        }
        String statement = cleaned.toString().trim();
        if (statement.isEmpty()) {
            return;
        }
        if (statements.size() >= MAX_STATEMENTS) {
            throw new BusinessException("Sauvegarde trop volumineuse : nombre d'instructions maximal dépassé.");
        }
        statements.add(statement);
    }

    /**
     * Vérifie que l'instruction fait partie des formes produites par {@link #exportBackup()}.
     * Toute autre instruction (DROP DATABASE, GRANT, CREATE USER, LOAD_FILE, …) est refusée.
     */
    private void assertStatementAllowed(String statement) {
        String stmt = statement.trim();
        if (stmt.endsWith(";")) {
            stmt = stmt.substring(0, stmt.length() - 1).trim();
        }
        if (SET_FOREIGN_KEY_CHECKS.matcher(stmt).matches()
                || DROP_TABLE.matcher(stmt).matches()
                || isSingleParenthesised(CREATE_TABLE_HEAD, stmt)
                || isSingleParenthesised(INSERT_VALUES_HEAD, stmt)) {
            return;
        }
        throw new BusinessException("Instruction refusée lors de la restauration : « "
                + abbreviate(stmt) + " ». Seules les instructions d'une sauvegarde générée "
                + "par l'application sont acceptées.");
    }

    /**
     * Vrai si l'instruction commence par {@code head} et se termine juste après la
     * parenthèse fermante correspondante : aucun SQL ne peut donc être ajouté après
     * la partie de données (par exemple un {@code SELECT … INTO OUTFILE}).
     */
    private boolean isSingleParenthesised(Pattern head, String statement) {
        Matcher matcher = head.matcher(statement);
        if (!matcher.find()) {
            return false;
        }
        int open = matcher.end() - 1;
        int close = matchingParenthesis(statement, open);
        if (close <= open) {
            return false;
        }
        return statement.substring(close + 1).trim().isEmpty();
    }

    private String abbreviate(String statement) {
        String oneLine = statement.replace('\n', ' ').trim();
        return oneLine.length() > 80 ? oneLine.substring(0, 80) + "…" : oneLine;
    }
}
