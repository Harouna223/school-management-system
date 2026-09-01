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
        String ddl = definitions.get(0);
        int open = ddl.indexOf('(');
        int close = ddl.lastIndexOf(')');
        return ddl.substring(open + 1, close);
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
            return "'" + s.replace("'", "''") + "'";
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
     * Restaure un dump SQL produit par {@link #exportBackup()}.
     * Refuse tout fichier ne commençant pas par le marqueur {@value #BACKUP_MARKER}.
     */
    @Transactional
    public int restoreBackup(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();
            if (firstLine == null || !firstLine.startsWith(BACKUP_MARKER)) {
                throw new BusinessException("Fichier non reconnu : utilisez une sauvegarde générée par l'application.");
            }

            StringBuilder statements = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                statements.append(line).append('\n');
            }

            String[] parts = statements.toString().split(";\\s*\\n");
            int executed = 0;
            for (String part : parts) {
                String stmt = part.trim();
                if (stmt.isEmpty()) continue;
                jdbcTemplate.execute(stmt);
                executed++;
            }
            return executed;
        } catch (IOException ex) {
            throw new BusinessException("Impossible de lire le fichier de sauvegarde.");
        }
    }
}
