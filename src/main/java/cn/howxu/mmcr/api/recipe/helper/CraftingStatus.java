package cn.howxu.mmcr.api.recipe.helper;

public final class CraftingStatus {

    public enum Status {
        IDLE,
        CRAFTING,
        MISSING_STRUCTURE,
        CHUNK_UNLOADED,
        NO_RECIPE,
        PAUSED
    }

    public static final CraftingStatus IDLE = new CraftingStatus(Status.IDLE, "");
    public static final CraftingStatus MISSING_STRUCTURE = new CraftingStatus(Status.MISSING_STRUCTURE, "");
    public static final CraftingStatus CHUNK_UNLOADED = new CraftingStatus(Status.CHUNK_UNLOADED, "");

    private final Status status;
    private String unlocalizedMessage;

    public CraftingStatus(Status status, String unlocalizedMessage) {
        this.status = status;
        this.unlocalizedMessage = unlocalizedMessage == null ? "" : unlocalizedMessage;
    }

    public static CraftingStatus working() {
        return new CraftingStatus(Status.CRAFTING, "");
    }

    public static CraftingStatus working(String unlocMessage) {
        return new CraftingStatus(Status.CRAFTING, unlocMessage);
    }

    public static CraftingStatus paused() {
        return new CraftingStatus(Status.PAUSED, "");
    }

    public static CraftingStatus failure(String unlocMessage) {
        return new CraftingStatus(Status.NO_RECIPE, unlocMessage);
    }

    public Status getStatus() {
        return status;
    }

    public String getUnlocMessage() {
        return !unlocalizedMessage.isEmpty() ? unlocalizedMessage : defaultMessage(status);
    }

    public void overrideStatusMessage(String unlocalizedMessage) {
        this.unlocalizedMessage = unlocalizedMessage == null ? "" : unlocalizedMessage;
    }

    public boolean isCrafting() {
        return this.status == Status.CRAFTING || this.status == Status.PAUSED;
    }

    public boolean isPaused() {
        return this.status == Status.PAUSED;
    }

    public boolean isFailure() {
        return this.status == Status.NO_RECIPE;
    }

    private static String defaultMessage(Status status) {
        return switch (status) {
            case CRAFTING -> "mmcr.status.crafting";
            case MISSING_STRUCTURE -> "mmcr.status.missing_structure";
            case CHUNK_UNLOADED -> "mmcr.status.chunk_unloaded";
            case NO_RECIPE -> "mmcr.status.no_recipe";
            case PAUSED -> "mmcr.status.paused";
            default -> "";
        };
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CraftingStatus other)) return false;
        return status == other.status && unlocalizedMessage.equals(other.unlocalizedMessage);
    }

    @Override
    public int hashCode() {
        return 31 * status.hashCode() + unlocalizedMessage.hashCode();
    }
}
