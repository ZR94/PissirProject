package upo.pissir.mqtt;

public final class TopicParser {
    private TopicParser() {}

    public record Parsed(String tollboothId, String direction, String channel, String leaf) {}

    // highway/{tollboothId}/{entry|exit}/{manual|telepass}/{events|commands|responses|state|requests}
    public static Parsed parse(String topic) {
        String[] p = topic.split("/");
        if (p.length < 2 || !"highway".equals(p[0])) {
            throw new IllegalArgumentException("Invalid topic: " + topic);
        }

        // Global topic: highway/requests/tollprice
        if (p.length == 3 && "requests".equals(p[1])) {
            return new Parsed(null, null, "requests", p[2]);
        }

        if (p.length < 5) {
            throw new IllegalArgumentException("Invalid topic: " + topic);
        }

        String tollboothId = p[1];
        String direction = p[2]; // entry|exit
        String channel = p[3];   // manual|telepass|camera
        String leaf = p[4];      // events|commands|responses|state|requests

        return new Parsed(tollboothId, direction, channel, leaf);
    }
}
