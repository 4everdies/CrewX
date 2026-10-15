package crewx.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Diagnostic lines of the client, written to the game log (latest.log). */
public final class ClientLog {
    private static final Logger LOGGER = LogManager.getLogger("CrewX");

    private ClientLog() {
    }

    public static void log(String message) {
        LOGGER.info(message);
    }
}
