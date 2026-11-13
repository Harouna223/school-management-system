package com.school.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Vérifie au démarrage que le répertoire des fichiers pédagogiques est
 * réellement accessible en écriture.
 *
 * <p>Pourquoi cette classe existe : ce répertoire est un volume monté depuis
 * l'hôte, et le conteneur tourne en UID 10001. Si le montage appartient à
 * {@code root} — cas par défaut d'une création manuelle, d'un volume restauré
 * avec {@code tar} en root, ou d'un montage de disque Windows en {@code 9p} —
 * <strong>chaque téléversement échoue en « Permission denied »</strong>, alors
 * que la pile démarre normalement et que le conteneur se déclare sain. Le
 * symptôme visible est trompeur : « Élève enregistré, mais la photo n'a pas pu
 * être envoyée ». Rien dans les journaux ne reliait cette phrase à sa cause.</p>
 *
 * <p>L'échec est signalé mais <em>non bloquant</em>, volontairement : un
 * répertoire non inscriptible empêche d'ajouter une photo ou un document, pas
 * de consulter les notes ni d'encaisser un paiement. Faire échouer le démarrage
 * rendrait indisponible une application encore utile, pour un défaut
 * réparable en une commande. Le niveau {@code ERROR} est en revanche assumé :
 * c'est ce qui distingue ce cas d'une simple remarque.</p>
 */
@Component
@Slf4j
public class UploadDirectoryStartupCheck implements CommandLineRunner {

    private final Path uploadDir;

    public UploadDirectoryStartupCheck(AppProperties appProperties) {
        this.uploadDir = Paths.get(appProperties.getUploadDir()).toAbsolutePath().normalize();
    }

    @Override
    public void run(String... args) {
        try {
            Files.createDirectories(uploadDir);
            // Un simple `Files.isWritable` ne suffit pas : il peut répondre vrai
            // sur un montage réseau ou 9p dont l'écriture échoue ensuite. Seule
            // une écriture réelle prouve que le volume accepte les fichiers.
            Path probe = Files.createTempFile(uploadDir, ".probe-", ".tmp");
            Files.delete(probe);
            log.info("Répertoire des fichiers pédagogiques accessible en écriture : {}", uploadDir);
        } catch (IOException | RuntimeException ex) {
            log.error("""
                    TELEVERSEMENTS IMPOSSIBLES : le répertoire {} n'est pas accessible en écriture \
                    pour l'utilisateur « {} » ({}). Les photos d'élèves, les documents et les \
                    vidéos de cours seront refusés en HTTP 400, alors que le reste de \
                    l'application continue de fonctionner.
                    Cause : ce répertoire est un volume monté depuis l'hôte ; il doit appartenir \
                    à l'UID 10001 utilisé par le conteneur.
                    Correction (sur l'hôte, en root) : sudo chown -R 10001:10001 {}
                    Puis : sudo systemctl restart sahel-edu.service
                    Détail technique : {}""",
                    uploadDir, System.getProperty("user.name"), System.getProperty("user.dir"),
                    uploadDir, ex.getMessage());
        }
    }
}
