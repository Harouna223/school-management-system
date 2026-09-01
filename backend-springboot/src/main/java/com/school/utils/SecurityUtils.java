package com.school.utils;

import com.school.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Accès à l'utilisateur courant authentifié.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    public static Long currentUserId() {
        User user = currentUser();
        return user != null ? user.getId() : null;
    }

    public static String currentUsername() {
        User user = currentUser();
        return user != null ? user.getUsername() : "anonymous";
    }

    public static boolean hasRole(String role) {
        User user = currentUser();
        return user != null && user.getRoles().stream()
                .anyMatch(r -> r.getName().equalsIgnoreCase(role));
    }
}
