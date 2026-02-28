import axios from "axios";

function readBackendMessage(data: unknown): string | null {
  if (typeof data === "string" && data.trim().length > 0) return data.trim();
  if (data && typeof data === "object") {
    const rec = data as Record<string, unknown>;
    for (const key of ["message", "error", "detail"]) {
      const v = rec[key];
      if (typeof v === "string" && v.trim().length > 0) return v.trim();
    }
  }
  return null;
}

export function toUserErrorMessage(
  error: unknown,
  fallback = "Unexpected error. Please try again.",
): string {
  if (axios.isAxiosError(error)) {
    if (error.code === "ECONNABORTED") {
      return "Request timed out. Please retry.";
    }

    if (!error.response) {
      return "Backend is unreachable. Check server status and network.";
    }

    const status = error.response.status;
    const backendMessage = readBackendMessage(error.response.data);

    if (status === 400) return backendMessage ?? "Invalid request. Check input fields.";
    if (status === 401) return "Your session is invalid or expired. Please log in again.";
    if (status === 403) return "You do not have permission for this action.";
    if (status === 404) return backendMessage ?? "Requested resource not found.";
    if (status === 409) return backendMessage ?? "Operation conflicts with current data.";
    if (status >= 500) return "Server error. Please try again later.";

    return backendMessage ?? fallback;
  }

  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }

  return fallback;
}
