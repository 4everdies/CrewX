package crewx.media.backend;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class WindowsMediaBackendTest {
    @Test
    public void usesTheVersionedCoverFileReportedByTheHelper() throws Exception {
        File cover = File.createTempFile("cover", ".img.2");
        cover.deleteOnExit();
        JsonObject json = new JsonObject();
        json.addProperty("art", 2);
        json.addProperty("artFile", cover.getAbsolutePath());
        assertEquals("file:" + cover.getAbsolutePath(), WindowsMediaBackend.artworkUrl(json, new File("missing")));
    }

    @Test
    public void noCoverWhenTheReportedFileIsGoneOrMissing() {
        JsonObject json = new JsonObject();
        json.addProperty("art", 0);
        json.addProperty("artFile", "");
        assertEquals("", WindowsMediaBackend.artworkUrl(json, new File("missing")));
        json.addProperty("artFile", new File("does-not-exist.img.9").getAbsolutePath());
        assertEquals("", WindowsMediaBackend.artworkUrl(json, new File("missing")));
    }
}
