package mage.player.ai;

/**
 * Raised when MAGELLM_STRICT_DECISIONS is set and a decision could not be served.
 *
 * <p>Extends {@link Error}, deliberately. Strict mode exists so that a broken serving
 * path fails its game instead of quietly handing the decision to ComputerPlayer7, and
 * an exception cannot deliver that guarantee here: the paths this has to travel are
 * lined with {@code catch (Exception)} handlers that would each turn the failure back
 * into a heuristic decision.
 *
 * <p>Two of them defeated strict mode outright. {@code ComputerPlayer8.choose} and
 * {@code ComputerPlayer8.chooseTarget} catch {@code Exception} and fall through to the
 * CP7 heuristic, and {@code GameImpl}'s inner priority handler catches {@code Exception},
 * rolls back to the priority bookmark and continues (its fail-fast branch is gated on
 * {@code activePlayer.isTestsMode()}, and the benchmark base class sets
 * {@code gameOptions.testMode = false}). The result was two full benchmark reports that
 * recorded {@code strict_decisions: True}, counted five figures of fallbacks, and still
 * published a win rate — see
 * {@code memory-bank/progress/benchmark_strict_decisions_defect.md}.
 *
 * <p>An {@code Error} passes through all of them. Do not "fix" a compiler warning by
 * catching this; if a new call site needs to clean up, use try/finally.
 */
public class StrictDecisionFailure extends Error {

    private static final long serialVersionUID = 1L;

    public StrictDecisionFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
