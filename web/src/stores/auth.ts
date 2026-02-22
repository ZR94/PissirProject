import { defineStore } from "pinia";
import { getKeycloak } from "@/auth/keycloak";

type AuthState = {
  isAuthenticated: boolean;
  username: string | null;
  roles: string[];
};

export const useAuthStore = defineStore("auth", {
  state: (): AuthState => ({
    isAuthenticated: false,
    username: null,
    roles: [],
  }),
  actions: {
    syncFromKeycloak() {
      const kc = getKeycloak();
      this.isAuthenticated = !!kc.authenticated;

      const tokenParsed = kc.tokenParsed as Record<string, unknown> | undefined;
      this.username =
        (tokenParsed?.preferred_username as string | undefined) ?? null;

      const realmAccess = tokenParsed?.realm_access as { roles?: string[] } | undefined;
      this.roles = realmAccess?.roles ?? [];
    },
    hasRole(role: string): boolean {
      return this.roles.includes(role);
    },
    hasAnyRole(roles: string[]): boolean {
      return roles.some((role) => this.roles.includes(role));
    },
    login() {
      getKeycloak().login();
    },
    logout() {
      getKeycloak().logout({ redirectUri: window.location.origin });
    },
    getAccessToken(): string | null {
      return getKeycloak().token ?? null;
    },
  },
});
