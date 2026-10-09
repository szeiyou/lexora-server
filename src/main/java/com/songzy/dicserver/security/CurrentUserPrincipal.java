package com.songzy.dicserver.security;

import java.security.Principal;

public record CurrentUserPrincipal(Long userId, String username) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
