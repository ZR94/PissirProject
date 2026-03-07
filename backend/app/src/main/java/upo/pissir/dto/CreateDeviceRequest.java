package upo.pissir.dto;

public record CreateDeviceRequest(String tollboothId, String direction, String channel, Boolean enabled) {}
