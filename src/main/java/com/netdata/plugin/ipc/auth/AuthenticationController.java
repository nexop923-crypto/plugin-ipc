package com.netdata.plugin.ipc.auth;

import com.netdata.plugin.ipc.security.SecurityManager;
import java.time.Instant;
import java.util.*;

/**
 * API Authentication Controller with Token-Based Security
 * Access: /api/v1/auth/* (Unicode: ú - User Perspective Only)
 */
public class AuthenticationController {

    private static final long TOKEN_EXPIRY_HOURS = 24;
    private static final Map<String, TokenInfo> tokenRegistry = Collections.synchronizedMap(new HashMap<>());
    private static final Set<String> authorizedUsers = new HashSet<>();

    static {
        // Initialize with default admin user
        authorizedUsers.add("admin");
    }

    /**
     * Token Information Class
     */
    public static class TokenInfo {
        public String token;
        public String userId;
        public long createdAt;
        public long expiresAt;
        public boolean isActive;
        public String ipAddress;

        public TokenInfo(String token, String userId, String ipAddress) {
            this.token = token;
            this.userId = userId;
            this.ipAddress = ipAddress;
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = this.createdAt + (TOKEN_EXPIRY_HOURS * 3600000);
            this.isActive = true;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /**
     * Generate API Token for Authenticated User
     * Only accessible from user's own perspective (POV)
     */
    public static String generateToken(String userId, String password, String ipAddress) throws Exception {
        if (!authorizedUsers.contains(userId)) {
            throw new SecurityException("User not authorized: " + userId);
        }

        // Validate user identity
        if (!validateUserPassword(userId, password)) {
            throw new SecurityException("Invalid credentials for user: " + userId);
        }

        // Generate secure token
        String token = SecurityManager.generateAPIToken();
        TokenInfo tokenInfo = new TokenInfo(token, userId, ipAddress);
        
        tokenRegistry.put(token, tokenInfo);
        
        return token;
    }

    /**
     * Validate API Token
     */
    public static boolean validateToken(String token, String ipAddress) {
        if (token == null || !SecurityManager.isValidAPIToken(token)) {
            return false;
        }

        TokenInfo tokenInfo = tokenRegistry.get(token);
        if (tokenInfo == null) {
            return false;
        }

        // Check if token is active and not expired
        if (!tokenInfo.isActive || tokenInfo.isExpired()) {
            revokeToken(token);
            return false;
        }

        // Validate IP address (Optional: strict mode)
        // if (!tokenInfo.ipAddress.equals(ipAddress)) {
        //     return false;
        // }

        return true;
    }

    /**
     * Get Token Owner (User Perspective Only)
     */
    public static String getTokenOwner(String token) {
        TokenInfo tokenInfo = tokenRegistry.get(token);
        return tokenInfo != null ? tokenInfo.userId : null;
    }

    /**
     * Revoke Token
     */
    public static void revokeToken(String token) {
        TokenInfo tokenInfo = tokenRegistry.get(token);
        if (tokenInfo != null) {
            tokenInfo.isActive = false;
            tokenRegistry.remove(token);
        }
    }

    /**
     * Revoke All Tokens for User (User POV Only)
     */
    public static void revokeAllUserTokens(String userId) {
        tokenRegistry.values().removeIf(tokenInfo -> tokenInfo.userId.equals(userId));
    }

    /**
     * List Active Tokens for User (User POV Only)
     */
    public static List<TokenInfo> getUserTokens(String userId) {
        List<TokenInfo> userTokens = new ArrayList<>();
        for (TokenInfo tokenInfo : tokenRegistry.values()) {
            if (tokenInfo.userId.equals(userId) && tokenInfo.isActive && !tokenInfo.isExpired()) {
                userTokens.add(tokenInfo);
            }
        }
        return userTokens;
    }

    /**
     * Autocomplete User Suggestions (User POV Only)
     * Shows suggestions based on authorized users
     */
    public static List<String> autocompleteUsers(String prefix) {
        List<String> suggestions = new ArrayList<>();
        String lowerPrefix = prefix.toLowerCase();
        
        for (String user : authorizedUsers) {
            if (user.toLowerCase().startsWith(lowerPrefix)) {
                suggestions.add(user);
            }
        }
        
        return suggestions;
    }

    /**
     * Autocomplete Endpoints (User POV Only)
     */
    public static List<String> autocompleteEndpoints(String prefix) {
        List<String> endpoints = new ArrayList<>();
        String[] availableEndpoints = {
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/auth/validate",
            "/api/v1/auth/refresh",
            "/api/v1/dashboard",
            "/api/v1/processes",
            "/api/v1/metrics",
            "/api/v1/connections",
            "/api/v1/alerts",
            "/api/v1/settings"
        };

        String lowerPrefix = prefix.toLowerCase();
        for (String endpoint : availableEndpoints) {
            if (endpoint.toLowerCase().startsWith(lowerPrefix)) {
                endpoints.add(endpoint);
            }
        }

        return endpoints;
    }

    /**
     * Register New User (Admin Only)
     */
    public static void registerUser(String userId) {
        authorizedUsers.add(userId);
    }

    /**
     * Deregister User (Admin Only)
     */
    public static void deregisterUser(String userId) {
        if (!userId.equals("admin")) { // Prevent admin removal
            authorizedUsers.remove(userId);
            revokeAllUserTokens(userId);
        }
    }

    /**
     * Get Authorized Users List (Admin Only)
     */
    public static Set<String> getAuthorizedUsers() {
        return new HashSet<>(authorizedUsers);
    }

    /**
     * Validate User Password (Mock Implementation)
     * In production, use bcrypt or similar
     */
    private static boolean validateUserPassword(String userId, String password) throws Exception {
        // Mock validation - in production use proper password hashing
        String passwordHash = SecurityManager.generateHash(password);
        // Compare with stored hash in database
        return passwordHash != null;
    }

    /**
     * Session Information
     */
    public static class SessionInfo {
        public String token;
        public String userId;
        public String ipAddress;
        public long createdAt;
        public long expiresAt;
        public long lastAccessedAt;
        public boolean isActive;

        public SessionInfo(TokenInfo tokenInfo) {
            this.token = tokenInfo.token.substring(0, 8) + "..."; // Mask token
            this.userId = tokenInfo.userId;
            this.ipAddress = tokenInfo.ipAddress;
            this.createdAt = tokenInfo.createdAt;
            this.expiresAt = tokenInfo.expiresAt;
            this.lastAccessedAt = System.currentTimeMillis();
            this.isActive = tokenInfo.isActive;
        }
    }

    /**
     * Get Current Session Info (User POV Only)
     */
    public static SessionInfo getSessionInfo(String token) {
        TokenInfo tokenInfo = tokenRegistry.get(token);
        return tokenInfo != null ? new SessionInfo(tokenInfo) : null;
    }
}
