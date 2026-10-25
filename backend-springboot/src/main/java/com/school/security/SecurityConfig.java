package com.school.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration Spring Security : JWT, CORS, session stateless, règles par endpoint.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtExceptionFilter jwtExceptionFilter;
    private final CustomUserDetailsService userDetailsService;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoints publics (connexion uniquement)
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/actuator/health", "/actuator/health/**").permitAll()
                        // Création de compte : réservée aux gestionnaires d'utilisateurs
                        .requestMatchers("/api/auth/register")
                        .hasAnyAuthority("PERM_USER_WRITE")
                        // Les journaux contiennent des données personnelles : cette règle
                        // précise doit précéder la règle générale des communications.
                        .requestMatchers(HttpMethod.GET, "/api/communication/message-logs/**")
                        .hasAnyAuthority("PERM_USER_READ", "ROLE_SUPER_ADMIN", "ROLE_ADMIN")
                        // Accès par permission sur les modules (READ / WRITE)
                        .requestMatchers(HttpMethod.GET, "/api/students/**").hasAnyAuthority("PERM_STUDENT_READ")
                        .requestMatchers("/api/students/**").hasAnyAuthority("PERM_STUDENT_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/teachers/**").hasAnyAuthority("PERM_TEACHER_READ")
                        .requestMatchers("/api/teachers/**").hasAnyAuthority("PERM_TEACHER_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/classes/**").hasAnyAuthority("PERM_CLASS_READ")
                        .requestMatchers("/api/classes/**").hasAnyAuthority("PERM_CLASS_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/subjects/**").hasAnyAuthority("PERM_SUBJECT_READ")
                        .requestMatchers("/api/subjects/**").hasAnyAuthority("PERM_SUBJECT_WRITE")
                        // Le bulletin PDF est un document PERSONNEL : l'accès réel est
                        // déjà cloisonné par AccessControlService.assertCanAccessStudent
                        // (appelé par GradeService.exportBulletinPdf). L'exiger sous
                        // PERM_EXAM_READ renvoyait un 403 à tout élève et à tout parent
                        // (ce rôle ne détient pas EXAM_READ) : le bouton « Télécharger le
                        // PDF » du portail élève était donc inutilisable. GRADE_READ est
                        // détenu par ELEVE et PARENT, mais pas par COMPTABLE.
                        .requestMatchers(HttpMethod.GET, "/api/exams/bulletins/*/pdf")
                        .hasAnyAuthority("PERM_EXAM_READ", "PERM_GRADE_READ")
                        .requestMatchers(HttpMethod.GET, "/api/exams/**").hasAnyAuthority("PERM_EXAM_READ")
                        .requestMatchers("/api/exams/**").hasAnyAuthority("PERM_EXAM_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/attendances/**").hasAnyAuthority("PERM_ATTENDANCE_READ")
                        .requestMatchers("/api/attendances/**").hasAnyAuthority("PERM_ATTENDANCE_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/schedules/**").hasAnyAuthority("PERM_SCHEDULE_READ")
                        .requestMatchers("/api/schedules/**").hasAnyAuthority("PERM_SCHEDULE_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/payments/**").hasAnyAuthority("PERM_PAYMENT_READ")
                        .requestMatchers("/api/payments/**").hasAnyAuthority("PERM_PAYMENT_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/finance/**").hasAnyAuthority("PERM_FINANCE_READ")
                        .requestMatchers("/api/finance/**").hasAnyAuthority("PERM_FINANCE_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/hr/**").hasAnyAuthority("PERM_HR_READ")
                        .requestMatchers("/api/hr/**").hasAnyAuthority("PERM_HR_WRITE")
                        // Gestion des heures enseignées / paie des enseignants à l'heure :
                        // lecture (heures, salaires, rapports, reçus), paiement réservé
                        // HOURS_PAY, saisie/correction des heures HOURS_WRITE
                        .requestMatchers(HttpMethod.GET, "/api/teacher-hours/**")
                        .hasAnyAuthority("PERM_HOURS_READ")
                        .requestMatchers("/api/teacher-hours/payments/**")
                        .hasAnyAuthority("PERM_HOURS_PAY")
                        .requestMatchers("/api/teacher-hours/**")
                        .hasAnyAuthority("PERM_HOURS_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/library/**").hasAnyAuthority("PERM_LIBRARY_READ")
                        .requestMatchers("/api/library/**").hasAnyAuthority("PERM_LIBRARY_WRITE")
                        // Cours et ressources : les trois endpoints binaires sont ouverts au
                        // porteur d'un jeton de lecture — un <video> ou un <iframe> ne peut pas
                        // envoyer l'en-tête Authorization. Le jeton est signé, court, limité à
                        // une ressource et à un usage, et les droits réels sont revalidés dans
                        // CourseResourceService.resolveFile à chaque requête.
                        .requestMatchers(HttpMethod.GET,
                                "/api/courses/*/stream",
                                "/api/courses/*/preview",
                                "/api/courses/*/download").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/courses/**").hasAnyAuthority("PERM_COURSE_READ")
                        .requestMatchers("/api/courses/**").hasAnyAuthority("PERM_COURSE_WRITE")
                        // Un message est borné à ses DEUX participants (contrôle dans
                        // MessageService) : ouvrir une conversation, la marquer lue,
                        // y répondre ou la supprimer ne peut donc pas exiger
                        // COMMUNICATION_WRITE — les comptes parent et élève, qui sont
                        // des destinataires légitimes, ne le détiennent pas.
                        .requestMatchers(HttpMethod.GET, "/api/communication/messages/*").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/communication/messages/*/read").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/communication/messages/*/reply").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/communication/messages/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/communication/**").hasAnyAuthority("PERM_COMMUNICATION_READ")
                        .requestMatchers("/api/communication/**").hasAnyAuthority("PERM_COMMUNICATION_WRITE")
                        // Barèmes d'appréciation : configuration réservée aux administrateurs
                        // autorisés (lecture : direction + secrétariat, écriture : direction).
                        .requestMatchers(HttpMethod.GET, "/api/appreciation-scales/**")
                        .hasAnyAuthority("PERM_APPRECIATION_READ")
                        .requestMatchers("/api/appreciation-scales/**")
                        .hasAnyAuthority("PERM_APPRECIATION_WRITE")
                        // Recherche globale : réservée aux rôles d'administration (expose données personnelles et financières)
                        .requestMatchers(HttpMethod.GET, "/api/search/**")
                        .hasAnyAuthority("PERM_REPORT_READ", "PERM_USER_READ")
                        .requestMatchers(HttpMethod.GET, "/api/academic-years/**").hasAnyAuthority("PERM_ACADEMIC_YEAR_READ")
                        .requestMatchers("/api/academic-years/**").hasAnyAuthority("PERM_ACADEMIC_YEAR_WRITE")
                        // Convocations : lecture par l'élève, l'étudiant et le parent (IDOR contrôlé côté service),
                        // écriture par l'administration
                        // Liste des dossiers parents (sélecteur de convocation) :
                        // donnée d'établissement. STUDENT_READ ne suffit pas — un
                        // parent ou un élève le détient.
                        .requestMatchers(HttpMethod.GET, "/api/parents").hasAnyAuthority("PERM_STUDENT_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/convocations/**").authenticated()
                        .requestMatchers("/api/convocations/**").hasAnyAuthority("PERM_STUDENT_WRITE")
                        // Stages : mêmes règles d'autorisation que les autres modules annexes
                        .requestMatchers(HttpMethod.GET, "/api/stages/**").hasAnyAuthority("PERM_LMD_READ")
                        .requestMatchers("/api/stages/**").hasAnyAuthority("PERM_LMD_WRITE")
                        // Admission, mémoires, alumni : mêmes règles que les autres modules annexes
                        .requestMatchers(HttpMethod.GET, "/api/candidatures/**").hasAnyAuthority("PERM_LMD_READ")
                        .requestMatchers("/api/candidatures/**").hasAnyAuthority("PERM_LMD_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/memoires/**").hasAnyAuthority("PERM_LMD_READ")
                        .requestMatchers("/api/memoires/**").hasAnyAuthority("PERM_LMD_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/alumni/**").hasAnyAuthority("PERM_LMD_READ")
                        .requestMatchers("/api/alumni/**").hasAnyAuthority("PERM_LMD_WRITE")
                        .requestMatchers("/api/backup/**").hasAuthority("PERM_BACKUP_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/users/**", "/api/roles/**", "/api/audit/**")
                        .hasAnyAuthority("PERM_USER_READ", "ROLE_SUPER_ADMIN", "ROLE_ADMIN")
                        .requestMatchers("/api/users/**", "/api/roles/**", "/api/audit/**")
                        .hasAnyAuthority("PERM_USER_WRITE", "ROLE_SUPER_ADMIN", "ROLE_ADMIN")
                        // Les statistiques globales contiennent notamment des données financières.
                        .requestMatchers(HttpMethod.GET,
                                "/api/dashboard/stats")
                        .hasAnyAuthority("PERM_REPORT_READ", "ROLE_SUPER_ADMIN", "ROLE_ADMIN")
                        // Journal d'audit : lecture réservée aux rôles autorisés
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/audit-logs/**")
                        .hasAnyAuthority("PERM_USER_READ")
                        // Cycles d'enseignement : lecture pour tous les utilisateurs connectés,
                        // modification réservée à l'administrateur.
                        .requestMatchers(HttpMethod.GET, "/api/settings/cycles")
                        .authenticated()
                        .requestMatchers("/api/settings/**").hasRole("SUPER_ADMIN")
                        // Toute autre requête exige une authentification
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"Non authentifié : jeton manquant ou invalide\","
                                            + "\"path\":\"" + request.getRequestURI() + "\",\"timestamp\":\""
                                            + java.time.LocalDateTime.now() + "\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"Accès refusé : permissions insuffisantes\","
                                            + "\"path\":\"" + request.getRequestURI() + "\",\"timestamp\":\""
                                            + java.time.LocalDateTime.now() + "\"}");
                        }))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtExceptionFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS : origines configurées (frontend dev / production).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}