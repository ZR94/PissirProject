package upo.pissir.toll;

/**
 * Single-device launcher.
 * Legacy "all-in-one toll service" mode is intentionally disabled.
 */
public class Main {

    public static void main(String[] args) {
        String mode = env("DEVICE_MODE", "");
        switch (mode) {
            case "entry-manual" -> MainEntryManual.main(args);
            case "entry-telepass" -> MainEntryTelepass.main(args);
            case "exit-manual" -> MainExitManual.main(args);
            case "exit-telepass" -> MainExitTelepass.main(args);
            default -> {
                String msg = "Invalid DEVICE_MODE. Use one of: entry-manual, entry-telepass, exit-manual, exit-telepass";
                System.err.println(msg);
                throw new IllegalArgumentException(msg);
            }
        }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
