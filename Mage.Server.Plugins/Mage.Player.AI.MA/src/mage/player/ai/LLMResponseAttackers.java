package mage.player.ai;

import java.util.List;
import java.util.UUID;

public class LLMResponseAttackers {
    private List<UUID> chosenAttackersUUIDs;
    private String reason;

    public LLMResponseAttackers(List<UUID> chosenAttackersUUIDs, String reason) {
        this.chosenAttackersUUIDs = chosenAttackersUUIDs;
        this.reason = reason;
    }

    public List<UUID> getChosenAttackersUUIDs() {
        return chosenAttackersUUIDs;
    }

    public String getReason() {
        return reason;
    }

}