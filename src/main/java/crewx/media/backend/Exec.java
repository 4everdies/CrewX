package crewx.media.backend;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs a short-lived command and captures stdout. */
final class Exec {
    static final class Output {
        final int exitCode;
        final String text;

        Output(int exitCode, String text) {
            this.exitCode = exitCode;
            this.text = text;
        }
    }

    /** The program could not be started at all (not installed / not executable). */
    static final class ToolMissingException extends IOException {
        ToolMissingException(IOException cause) {
            super(cause.getMessage(), cause);
        }
    }

    private Exec() {
    }

    static Output run(List<String> command, long timeoutMs) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException missing) {
            throw new ToolMissingException(missing);
        }
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            Thread reaper = new Thread(() -> {
                try {
                    if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) process.destroyForcibly();
                } catch (InterruptedException ignored) {
                    process.destroyForcibly();
                }
            }, "CrewX media exec watchdog");
            reaper.setDaemon(true);
            reaper.start();
            String line;
            while ((line = reader.readLine()) != null) {
                if (text.length() > 0) text.append('\n');
                text.append(line);
            }
        }
        int exit = process.waitFor();
        return new Output(exit, text.toString());
    }
}
