package upo.pissir.auth;

import java.util.List;

public record AuthUser(String sub, String username, List<String> roles) {
  public boolean hasRole(String role) {
    return roles != null && roles.contains(role);
  }
}
