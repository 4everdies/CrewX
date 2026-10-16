package crewx.media.backend;

import crewx.media.MediaAction;
import crewx.media.MediaInfo;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PollingBackendTest {
    private static final class Probe extends PollingBackend {
        final AtomicInteger polls = new AtomicInteger();
        volatile Exception failure;
        volatile MediaInfo result;

        Probe(long interval) {
            super(interval);
        }

        @Override
        MediaInfo poll() throws Exception {
            this.polls.incrementAndGet();
            if (this.failure != null) throw this.failure;
            return this.result;
        }

        @Override
        void execute(MediaAction action, long argument) {
        }

        @Override
        String unavailableMessage() {
            return "missing";
        }
    }

    @Test
    public void aMissingToolIsNotStartedAgainEverySecond() throws Exception {
        Probe probe = new Probe(20L);
        probe.failure = new Exec.ToolMissingException(new IOException("error=2, No such file or directory"));
        probe.start(info -> { }, status -> { });
        Thread.sleep(400L);
        probe.stop();
        assertEquals(1, probe.polls.get());
    }

    @Test
    public void aManualRefreshRetriesAMissingToolOnce() throws Exception {
        Probe probe = new Probe(20L);
        probe.failure = new Exec.ToolMissingException(new IOException("missing"));
        probe.start(info -> { }, status -> { });
        Thread.sleep(150L);
        probe.refresh();
        Thread.sleep(300L);
        probe.stop();
        assertEquals(2, probe.polls.get());
    }

    @Test
    public void otherFailuresKeepPolling() throws Exception {
        Probe probe = new Probe(20L);
        probe.failure = new IOException("timed out");
        probe.start(info -> { }, status -> { });
        Thread.sleep(500L);
        probe.stop();
        assertTrue("polls: " + probe.polls.get(), probe.polls.get() >= 3);
    }

    @Test
    public void pollsSlowerWhileNothingPlays() throws Exception {
        Probe idle = new Probe(40L);
        idle.start(info -> { }, status -> { });
        MediaInfo.Builder playing = MediaInfo.builder();
        playing.title = "Song";
        Probe busy = new Probe(40L);
        busy.result = playing.build();
        busy.start(info -> { }, status -> { });
        Thread.sleep(1300L);
        idle.stop();
        busy.stop();
        assertTrue("idle " + idle.polls.get() + " vs playing " + busy.polls.get(), idle.polls.get() * 2 < busy.polls.get());
    }
}
