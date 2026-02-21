package upo.pissir.toll.service;

public final class Commands {
    private Commands() {}

    public static final String ENTRY_MANUAL_COMMAND = "ENTRY_MANUAL_COMMAND";
    public static final String ENTRY_TELEPASS_COMMAND = "ENTRY_TELEPASS_COMMAND";
    public static final String EXIT_MANUAL_COMMAND = "EXIT_MANUAL_COMMAND";
    public static final String EXIT_TELEPASS_COMMAND = "EXIT_TELEPASS_COMMAND";

    public static final String REQUEST_ENTRY = "REQUEST_ENTRY"; // legacy
    public static final String REQUEST_EXIT = "REQUEST_EXIT";   // legacy
    public static final String INSERT_PAYMENT = "INSERT_PAYMENT";
    public static final String REQUEST_PAYMENT = "REQUEST_PAYMENT";
}

