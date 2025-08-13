package mage.player.ai;

import java.util.List;
import java.util.UUID;

/**
 * Minimal result wrapper for unified decision requests.
 */
public class DecisionResult {
    private final Integer chosenIndex; // for index-based selections
    private final List<UUID> chosenUuids; // for lists like attackers
    private final String reason;

    public DecisionResult(Integer chosenIndex, List<UUID> chosenUuids, String reason) {
        this.chosenIndex = chosenIndex;
        this.chosenUuids = chosenUuids;
        this.reason = reason;
    }

    public Integer getChosenIndex() {
        return chosenIndex;
    }

    public List<UUID> getChosenUuids() {
        return chosenUuids;
    }

    public String getReason() {
        return reason;
    }
}
