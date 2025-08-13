package org.mage.test.serverside.base;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

import org.json.JSONObject;

/**
 * Minimal AI vs AI smoke test to ensure at least one external LLM endpoint is
 * called.
 * This relies on ComputerPlayer8 wiring that calls
 * http://localhost:9000/api/mtg_llm/*.
 * Ensure magellm server is running in random strategy for fast tests.
 */
public class LLMIntegrationSmokeTest extends CardTestPlayerBaseAI {

    @Test
    public void test_AI_vs_AI_runs_and_makes_external_calls() {
        // reset counters before run
        httpPost("http://localhost:9000/api/mtg_llm/__test__/reset_counters", "{}");

        // Using default RB Aggro deck from base
        setStrictChooseMode(false);
        setStopOnTurn(2);

        execute();

        // query metrics and assert at least one endpoint was called
        JSONObject metrics = httpGetJson("http://localhost:9000/api/mtg_llm/__test__/metrics");
        JSONObject counters = metrics.getJSONObject("counters");
        int a = counters.optInt("choose_from_all_actions", 0);
        int b = counters.optInt("choose_from_choices", 0);
        int c = counters.optInt("choose_attackers", 0);
        assertTrue("Expected at least one external decision call, got: " + counters, (a + b + c) > 0);
    }

    private static void httpPost(String urlString, String body) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = body.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new RuntimeException("POST failed: " + urlString + ", code=" + code);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JSONObject httpGetJson(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new RuntimeException("GET failed: " + urlString + ", code=" + code);
            }
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                return new JSONObject(sb.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
