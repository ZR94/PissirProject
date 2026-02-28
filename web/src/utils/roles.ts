const HIDDEN_UI_ROLES = new Set([
  "offline_access",
  "uma_authorization",
  "default-roles-reti2",
  "administrators",
]);

export function visibleRolesForUi(roles: string[]): string[] {
  return roles.filter((role) => !HIDDEN_UI_ROLES.has(role));
}

export function roleLabel(role: string): string {
  if (role === "employees") return "Employee";
  if (role === "customers") return "Customer";
  return role;
}
