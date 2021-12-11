package work.fking.corpa.invsnaps;

public enum SnapshotReason {
    PLAYER_DEATH("Player died"),
    RESTORATION("Inventory restored");

    private final String fancyReason;

    SnapshotReason(String fancyReason) {
        this.fancyReason = fancyReason;
    }

    public String fancyReason() {
        return fancyReason;
    }
}
