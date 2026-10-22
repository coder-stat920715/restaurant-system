package com.example.restaurant.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public record SecurityContextAttributes(String userId, String locationId, Set<String> roles) {

    public static SecurityContextAttributes from(Authentication a) {
        var jwt = ((JwtAuthenticationToken) a).getToken();
        return new SecurityContextAttributes(
            jwt.getSubject(),
            jwt.getClaimAsString("restaurantLocationId"),
            a.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
    }

    public boolean has(String role) { return roles.contains(role); }
}
