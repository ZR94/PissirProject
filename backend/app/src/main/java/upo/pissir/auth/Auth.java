package upo.pissir.auth;

import com.nimbusds.jwt.JWTClaimsSet;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

public final class Auth {
  private Auth() {}

  public static AuthUser requireAuth(Context ctx) {
    String auth = ctx.header("Authorization");
    if (auth == null || !auth.startsWith("Bearer ")) {
      ctx.status(401).json(Map.of("error", "missing_bearer_token"));
      throw new Halt();
    }

    String token = auth.substring("Bearer ".length()).trim();

    JWTClaimsSet claims;
    try {
      claims = JwtVerifier.verify(token);
    } catch (Exception e) {
      ctx.status(401).json(Map.of("error", "invalid_token"));
      throw new Halt();
    }

    String sub = claims.getSubject();
    String username = safeString(claims.getClaim("preferred_username"));

    List<String> roles = extractRealmRoles(claims);

    return new AuthUser(sub, username, roles);
  }

  public static void requireRole(Context ctx, AuthUser user, String role) {
    if (!user.hasRole(role)) {
      ctx.status(403).json(Map.of("error", "forbidden"));
      throw new Halt();
    }
  }

  private static List<String> extractRealmRoles(JWTClaimsSet claims) {
    Object realmAccessObj = claims.getClaim("realm_access");
    if (realmAccessObj instanceof Map<?, ?> realmAccess) {
      Object rolesObj = realmAccess.get("roles");
      if (rolesObj instanceof List<?> roles) {
        return roles.stream().filter(String.class::isInstance).map(String.class::cast).toList();
      }
    }
    return List.of();
  }

  private static String safeString(Object o) {
    return (o instanceof String s) ? s : null;
  }

  public static final class Halt extends RuntimeException {}
}

