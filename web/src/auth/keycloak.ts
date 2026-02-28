import Keycloak from "keycloak-js";

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
});

export async function initKeycloak(): Promise<boolean> {
  return keycloak.init({
    onLoad: "login-required",
    redirectUri: `${window.location.origin}/`,
    pkceMethod: "S256",
    checkLoginIframe: false,
    responseMode: "query",
  });
}

export function getKeycloak() {
  return keycloak;
}

export async function refreshToken(minValiditySeconds = 30): Promise<void> {
  if (!keycloak.token) return;
  await keycloak.updateToken(minValiditySeconds);
}
