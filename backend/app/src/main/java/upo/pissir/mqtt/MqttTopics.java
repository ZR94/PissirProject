package upo.pissir.mqtt;

public final class MqttTopics {
    private MqttTopics() {}

    // Subscribe
    public static final String ENTRY_EVENTS = "highway/+/entry/+/events";
    public static final String EXIT_EVENTS  = "highway/+/exit/+/events";
    public static final String TOLLPRICE_REQUEST = "highway/requests/tollprice";
}
