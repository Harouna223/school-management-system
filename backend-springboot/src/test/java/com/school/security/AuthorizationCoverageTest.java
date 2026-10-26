package com.school.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde structurelle : <b>chaque endpoint doit être couvert par une règle
 * explicite de {@code SecurityConfig}</b>.
 *
 * <p>Pourquoi ce test existe. L'autorisation de ce projet est posée par chemin
 * dans {@code SecurityConfig}, et la chaîne se termine par
 * {@code .anyRequest().authenticated()} : tout endpoint qu'aucune règle ne
 * mentionne est donc ouvert à <em>n'importe quel compte connecté</em> — un
 * parent, un élève, un enseignant. Ajouter un contrôleur sans ajouter sa règle
 * ne produit aucune erreur, aucun log, et aucun test rouge : l'endpoint
 * fonctionne, simplement pour tout le monde. C'est exactement le type de défaut
 * qu'un test unitaire de service ne peut pas voir.</p>
 *
 * <p>Le test lit la <b>source</b> de {@code SecurityConfig} plutôt que de
 * dupliquer la liste des règles : dupliquer produirait une seconde vérité qui
 * dériverait, et surtout une règle <em>supprimée</em> du code ne ferait échouer
 * aucun miroir. Ici, retirer un {@code requestMatchers} casse le test.</p>
 *
 * <p>Les endpoints volontairement laissés à {@code anyRequest().authenticated()}
 * sont énumérés dans {@link #REPLIS_ASSUMES} avec leur justification : ils sont
 * cloisonnés par utilisateur dans le service, pas par permission. Cette liste est
 * le seul endroit à modifier pour assumer un nouveau repli — et il faut l'écrire.</p>
 */
class AuthorizationCoverageTest {

    private static final Path SECURITY_CONFIG =
            Path.of("src/main/java/com/school/security/SecurityConfig.java");
    private static final Path CONTROLLERS = Path.of("src/main/java/com/school/controller");

    /**
     * Endpoints sans règle dédiée, assumés : leur cloisonnement est fait dans le
     * service, par utilisateur courant, et non par permission.
     */
    private static final Map<String, String> REPLIS_ASSUMES = Map.of(
            "/uploads/**",
            "FileController : sert photos et documents ; anti-traversal, Cache-Control private et nosniff. "
                    + "Cloisonnement par permission impossible (une balise <img> n'envoie pas d'en-tête) — "
                    + "décision documentée dans le Javadoc du contrôleur.",
            "/api/my/**",
            "MySpaceController : espace personnel (enfants, notes, absences, paiements du compte courant) ; "
                    + "le service borne chaque requête à l'utilisateur authentifié.",
            "/api/parents/children/**",
            "ParentController : un parent lit SES enfants ; AccessControlService vérifie le lien parent-élève. "
                    + "La liste d'établissement GET /api/parents, elle, exige PERM_STUDENT_WRITE.",
            "/api/auth/**",
            "AuthController : login / refresh / logout sont publics et register exige PERM_USER_WRITE ; "
                    + "les endpoints restants (/me, changement de mot de passe) portent sur le compte courant.");

    /**
     * Tous les verbes. {@code HttpMethod} n'est PAS un enum en Spring 6 (c'est
     * une classe à constantes) : {@code EnumSet} est donc inutilisable ici.
     */
    private static final Set<HttpMethod> TOUTES = Set.of(
            HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH,
            HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.OPTIONS);

    private record Regle(Set<HttpMethod> methodes, String motif, String exigence) {
    }

    private record Endpoint(String methode, String chemin, String source) {
    }

    // =====================================================================

    @Test
    @DisplayName("la chaîne ne se termine jamais par un anyRequest().permitAll()")
    void anyRequestNeDoitJamaisEtreOuvert() throws IOException {
        String source = Files.readString(SECURITY_CONFIG, StandardCharsets.UTF_8);

        assertThat(source).contains(".anyRequest().authenticated()");
        assertThat(source).doesNotContain(".anyRequest().permitAll()");
        assertThat(source).doesNotContain(".anyRequest().hasAnyAuthority");
    }

    @Test
    @DisplayName("chaque endpoint est couvert par une règle explicite ou par un repli assumé")
    void chaqueEndpointEstCouvert() throws Exception {
        List<Regle> regles = lireRegles();
        assertThat(regles).as("règles lues dans SecurityConfig").hasSizeGreaterThan(30);

        AntPathMatcher matcher = new AntPathMatcher();
        List<String> nonCouverts = new ArrayList<>();

        for (Endpoint endpoint : lireEndpoints()) {
            boolean couvert = regles.stream().anyMatch(regle ->
                    regle.methodes().contains(HttpMethod.valueOf(endpoint.methode()))
                            && matcher.match(regle.motif(), endpoint.chemin()));

            if (!couvert && REPLIS_ASSUMES.keySet().stream()
                    .noneMatch(motif -> matcher.match(motif, endpoint.chemin()))) {
                nonCouverts.add(endpoint.methode() + " " + endpoint.chemin()
                        + "  (" + endpoint.source() + ")");
            }
        }

        assertThat(nonCouverts)
                .as("""
                        Endpoint(s) sans règle d'autorisation. Ils tombent sous
                        `anyRequest().authenticated()` : tout compte connecté y accède.
                        Ajouter une règle dans SecurityConfig, ou déclarer le repli dans
                        REPLIS_ASSUMES avec sa justification.""")
                .isEmpty();
    }

    @Test
    @DisplayName("les replis assumés correspondent à des endpoints qui existent encore")
    void replisAssumesToujoursUtilises() throws Exception {
        // Un repli qui ne couvre plus rien est une autorisation oubliée : on
        // veut être forcé de le retirer quand l'endpoint disparaît.
        List<Endpoint> endpoints = lireEndpoints();
        AntPathMatcher matcher = new AntPathMatcher();

        REPLIS_ASSUMES.forEach((motif, justification) -> {
            boolean utilise = endpoints.stream()
                    .anyMatch(e -> matcher.match(motif, e.chemin()));
            assertThat(utilise)
                    .as("repli « %s » sans endpoint correspondant — le retirer. Justification : %s",
                            motif, justification)
                    .isTrue();
        });
    }

    @Test
    @DisplayName("les règles de sécurité ne sont jamais toutes relatives à un seul module")
    void reglesCouvrentLesModulesSensibles() throws IOException {
        // Filet de sécurité sur les modules où un défaut coûte cher : si une règle
        // disparaît, le test précédent le voit, mais celui-ci nomme le module.
        String source = Files.readString(SECURITY_CONFIG, StandardCharsets.UTF_8);

        assertThat(source)
                .contains("/api/finance/**")
                .contains("/api/payments/**")
                .contains("/api/hr/**")
                .contains("/api/users/**")
                .contains("/api/backup/**")
                .contains("PERM_BACKUP_WRITE");
    }

    // =====================================================================
    // Lecture de SecurityConfig
    // =====================================================================

    /**
     * Extrait chaque {@code .requestMatchers(...)} suivi de son exigence.
     *
     * <p>Le motif accepte les appels répartis sur plusieurs lignes
     * ({@code .requestMatchers(HttpMethod.GET,\n "a",\n "b").permitAll()}), ce qui
     * est le cas des endpoints binaires des cours.</p>
     */
    private static List<Regle> lireRegles() throws IOException {
        String source = Files.readString(SECURITY_CONFIG, StandardCharsets.UTF_8);
        Pattern appel = Pattern.compile(
                "\\.requestMatchers\\(([^;]*?)\\)\\s*\\.\\s*(\\w+)\\(", Pattern.DOTALL);
        Pattern litteral = Pattern.compile("\"([^\"]+)\"");

        List<Regle> regles = new ArrayList<>();
        Matcher m = appel.matcher(source);
        while (m.find()) {
            String args = m.group(1);
            String exigence = m.group(2);

            Set<HttpMethod> methodes = new LinkedHashSet<>();
            Matcher methode = Pattern.compile("HttpMethod\\.(\\w+)").matcher(args);
            while (methode.find()) {
                methodes.add(HttpMethod.valueOf(methode.group(1)));
            }
            if (methodes.isEmpty()) {
                methodes = TOUTES;
            }

            Matcher chemins = litteral.matcher(args);
            while (chemins.find()) {
                String motif = chemins.group(1);
                if (motif.startsWith("/")) {
                    regles.add(new Regle(Set.copyOf(methodes), motif, exigence));
                }
            }
        }
        return regles;
    }

    // =====================================================================
    // Lecture des contrôleurs
    // =====================================================================

    private static List<Endpoint> lireEndpoints() throws Exception {
        List<Endpoint> endpoints = new ArrayList<>();
        try (Stream<Path> fichiers = Files.list(CONTROLLERS)) {
            List<Path> sources = fichiers
                    .filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .sorted()
                    .toList();

            for (Path fichier : sources) {
                String nom = fichier.getFileName().toString().replace(".java", "");
                Class<?> controleur = Class.forName("com.school.controller." + nom);
                String base = cheminClasse(controleur);

                for (Method methode : controleur.getDeclaredMethods()) {
                    for (Endpoint endpoint : endpointsDe(base, methode, nom)) {
                        endpoints.add(endpoint);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return endpoints;
    }

    private static String cheminClasse(Class<?> controleur) {
        org.springframework.web.bind.annotation.RequestMapping mapping =
                controleur.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
        if (mapping == null) {
            return "";
        }
        String[] valeurs = mapping.value().length > 0 ? mapping.value() : mapping.path();
        return valeurs.length > 0 ? valeurs[0] : "";
    }

    private static List<Endpoint> endpointsDe(String base, Method methode, String source) {
        List<Endpoint> resultat = new ArrayList<>();
        String nom = methode.getName();

        ajouter(resultat, base, source, nom, methode,
                org.springframework.web.bind.annotation.GetMapping.class, HttpMethod.GET);
        ajouter(resultat, base, source, nom, methode,
                org.springframework.web.bind.annotation.PostMapping.class, HttpMethod.POST);
        ajouter(resultat, base, source, nom, methode,
                org.springframework.web.bind.annotation.PutMapping.class, HttpMethod.PUT);
        ajouter(resultat, base, source, nom, methode,
                org.springframework.web.bind.annotation.PatchMapping.class, HttpMethod.PATCH);
        ajouter(resultat, base, source, nom, methode,
                org.springframework.web.bind.annotation.DeleteMapping.class, HttpMethod.DELETE);

        // @RequestMapping au niveau méthode : méthodes HTTP explicites.
        org.springframework.web.bind.annotation.RequestMapping mapping =
                methode.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
        if (mapping != null) {
            Set<HttpMethod> verbes = new LinkedHashSet<>();
            if (mapping.method().length > 0) {
                for (org.springframework.web.bind.annotation.RequestMethod rm : mapping.method()) {
                    verbes.add(HttpMethod.valueOf(rm.name()));
                }
            } else {
                verbes.addAll(TOUTES);
            }
            for (String chemin : cheminsDe(mapping.value(), mapping.path())) {
                for (HttpMethod verbe : verbes) {
                    resultat.add(new Endpoint(verbe.name(), normaliser(base + chemin), source + "#" + nom));
                }
            }
        }
        return resultat;
    }

    private static void ajouter(List<Endpoint> resultat, String base, String source, String nom,
                                Method methode, Class<? extends java.lang.annotation.Annotation> type,
                                HttpMethod verbe) {
        java.lang.annotation.Annotation annotation = methode.getAnnotation(type);
        if (annotation == null) {
            return;
        }
        for (String chemin : cheminsDe(annotation)) {
            resultat.add(new Endpoint(verbe.name(), normaliser(base + chemin), source + "#" + nom));
        }
    }

    /** Lit {@code value()} puis {@code path()} d'une annotation de mapping, par réflexion. */
    private static List<String> cheminsDe(java.lang.annotation.Annotation annotation) {
        try {
            Method value = annotation.annotationType().getMethod("value");
            Method path = annotation.annotationType().getMethod("path");
            String[] valeurs = (String[]) value.invoke(annotation);
            if (valeurs.length == 0) {
                valeurs = (String[]) path.invoke(annotation);
            }
            return valeurs.length == 0 ? List.of("") : List.of(valeurs);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("annotation de mapping inattendue : " + annotation, e);
        }
    }

    private static List<String> cheminsDe(String[] valeurs, String[] chemins) {
        String[] retenus = valeurs.length > 0 ? valeurs : chemins;
        return retenus.length == 0 ? List.of("") : List.of(retenus);
    }

    /**
     * Remplace les variables de chemin par une valeur concrète : le matcher Ant
     * raisonne sur des URL, pas sur {@code {id}}.
     */
    private static String normaliser(String chemin) {
        if (chemin.isEmpty()) {
            return "/";
        }
        String normalise = chemin.startsWith("/") ? chemin : "/" + chemin;
        normalise = normalise.replace("{*", "{").replaceAll("\\{[^}]+}", "x");
        return normalise.replaceAll("/+", "/");
    }

    /** Garde-fou : la liste des contrôleurs lue doit rester plausible. */
    @Test
    @DisplayName("les contrôleurs sont bien tous lus")
    void tousLesControleursSontLus() throws Exception {
        Set<String> sources = new LinkedHashSet<>();
        lireEndpoints().forEach(e -> sources.add(e.source().split("#")[0]));
        assertThat(sources).hasSizeGreaterThanOrEqualTo(30);
    }
}
