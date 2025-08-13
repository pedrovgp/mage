package mage.player.ai;

import org.json.JSONObject;

/**
 * Minimal payload wrapper for unified decision requests.
 */
public class DecisionPayload {
    private final String endpointPath; // e.g., "/choose_from_all_actions/"
    private final JSONObject body;

    public DecisionPayload(String endpointPath, JSONObject body) {
        this.endpointPath = endpointPath;
        this.body = body;
    }

    public String getEndpointPath() {
        return endpointPath;
    }

    public JSONObject getBody() {
        return body;
    }
}
