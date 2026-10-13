package com.school.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtre d'exception JWT : transforme les erreurs de jeton en réponse JSON 401 propre.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtExceptionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException ex) {
            handleError(response, "Jeton expiré, veuillez vous reconnecter");
        } catch (JwtException | IllegalArgumentException ex) {
            handleError(response, "Jeton invalide");
        } catch (UsernameNotFoundException ex) {
            // Jeton signé valide mais compte supprimé entre-temps : le filtre JWT
            // ne peut pas charger l'utilisateur. Sans cette branche, l'exception
            // remontait à Tomcat et produisait un 500 (y compris sur /auth/login
            // si le client renvoyait encore son ancien Bearer).
            log.debug("Jeton présentant un compte inexistant : {}", ex.getMessage());
            handleError(response, "Jeton invalide : compte introuvable");
        } catch (AuthenticationException ex) {
            log.debug("Échec d'authentification dans la chaîne de filtres : {}", ex.getMessage());
            handleError(response, "Authentification refusée");
        }
    }

    private void handleError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message + "\",\"timestamp\":\""
                        + java.time.LocalDateTime.now() + "\"}");
    }
}