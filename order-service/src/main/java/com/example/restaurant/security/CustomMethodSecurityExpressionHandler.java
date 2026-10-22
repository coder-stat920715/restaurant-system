package com.example.restaurant.security;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;

public class CustomMethodSecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler {
    public CustomMethodSecurityExpressionHandler(RoleHierarchy roleHierarchy, PermissionEvaluator evaluator) {
        setRoleHierarchy(roleHierarchy);
        setPermissionEvaluator(evaluator);
    }
}
