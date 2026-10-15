package crewx.media.backend;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Guards the Windows helper against the stdin call that froze it (no media was ever reported). */
public class SmtcScriptTest {
    private static String script() throws Exception {
        try (InputStream in = SmtcScriptTest.class.getResourceAsStream("/assets/crewx/media/smtc.ps1")) {
            assertNotNull("smtc.ps1 must be packaged", in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) out.write(chunk, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void mainLoopDoesNotReadConsoleInDirectly() throws Exception {
        String code = script().replaceAll("(?m)#.*$", "");
        // Console.In is a SyncTextReader: its ReadLineAsync() blocks, which stalled the poll loop forever
        assertFalse(code.contains("[Console]::In.ReadLineAsync"));
        assertFalse(code.contains("[Console]::In.ReadLine"));
        assertTrue(code.contains("[Console]::OpenStandardInput()"));
        assertTrue(code.contains("BeginInvoke()"));
    }
}
