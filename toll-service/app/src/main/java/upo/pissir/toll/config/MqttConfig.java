package upo.pissir.toll.config;

public record MqttConfig(
        String host,
        int port,
        boolean tlsEnabled,
        String username,
        String password,
        String caCrtPath
) {
    public static MqttConfig fromEnv() {
        String host = envAny("mosquitto_broker", "MQTT_HOST");
        int port = Integer.parseInt(envAny("1883", "MQTT_PORT"));
        boolean tls = Boolean.parseBoolean(envAny("false", "MQTT_TLS", "MQTT_TLS_ENABLED"));

        String user = envAny("toll", "MQTT_USER", "MQTT_USERNAME");
        String pass = envAny("pissirserver", "MQTT_PASS", "MQTT_PASSWORD");
        String ca = envAny("", "MQTT_CA_CRT", "MQTT_CA_CRT_PATH");

        return new MqttConfig(host, port, tls, user, pass, ca);
    }

    private static String envAny(String def, String... keys) {
        for (String key : keys) {
            String v = System.getenv(key);
            if (v != null && !v.isBlank()) return v;
        }
        return def;
    }
}
