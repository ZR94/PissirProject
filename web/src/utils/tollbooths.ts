import type { Tollbooth } from "@/types/tollbooth";

export function formatTollboothLabel(tollbooth: Tollbooth): string {
  const parts = [tollbooth.id];
  if (tollbooth.roadCode) {
    parts.push(tollbooth.roadCode);
  }
  if (tollbooth.kmMarker != null) {
    parts.push(`km ${tollbooth.kmMarker.toFixed(1)}`);
  }
  return parts.join(" · ");
}
