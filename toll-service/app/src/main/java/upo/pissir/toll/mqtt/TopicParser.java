package upo.pissir.toll.mqtt;

public final class TopicParser {
    private TopicParser() {}

    public record Parsed(String tollboothId, String direction, String channel, String leaf) {}

    // highway/{tollboothId}/{entry|exit}/{manual|telepass}/{commands|events|state}
    public static Parsed parse(String topic) {
        String[] p = topic.split("/");
        if (p.length < 2 || !"highway".equals(p[0])) {
            throw new IllegalArgumentException("Invalid topic: " + topic);
        }
        if (p.length < 5) {
            throw new IllegalArgumentException("Invalid topic: " + topic);
        }
        return new Parsed(p[1], p[2], p[3], p[4]);
    }
}
