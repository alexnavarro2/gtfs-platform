package mx.gtfsplatform.security;

import mx.gtfsplatform.domain.AppUser;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static AppUser get() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof AppUser user)) {
            throw new IllegalStateException("No hay un usuario autenticado en esta petición");
        }
        return user;
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(get().getRole());
    }
}
