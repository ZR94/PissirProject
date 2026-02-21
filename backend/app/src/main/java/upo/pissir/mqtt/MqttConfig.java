package upo.pissir.mqtt;

public record MqttConfig(
        String host,
        int port,
        String username,
        String password,
        boolean tlsEnabled,
        String caCrtPath
) {
    public static MqttConfig fromEnv() {
        String host = System.getenv().getOrDefault("MQTT_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("MQTT_PORT", "1883"));
        String user = System.getenv().getOrDefault("MQTT_USER", "server");
        String pass = System.getenv().getOrDefault("MQTT_PASS", "pissirserver");

        boolean tls = Boolean.parseBoolean(System.getenv().getOrDefault("MQTT_TLS", "false"));
        String ca = System.getenv().getOrDefault("MQTT_CA_CRT", "");

        return new MqttConfig(host, port, user, pass, tls, ca);
    }
}

