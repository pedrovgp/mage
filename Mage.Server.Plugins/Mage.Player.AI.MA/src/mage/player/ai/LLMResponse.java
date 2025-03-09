package mage.player.ai;

public class LLMResponse {
    private int chosenActionIndex;
    private String reason;

    public LLMResponse(int chosenActionIndex, String reason) {
        this.chosenActionIndex = chosenActionIndex;
        this.reason = reason;
    }

    public int getChosenActionIndex() {
        return chosenActionIndex;
    }

    public String getReason() {
        return reason;
    }

}