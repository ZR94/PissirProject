package upo.pissir.config;

public final class AppConfig {
  private AppConfig() {}

  public static int httpPort() {
    String v = System.getenv().getOrDefault("HTTP_PORT", "7070");
    return Integer.parseInt(v);
  }
}

