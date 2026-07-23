package myau.script;

import myau.util.ChatUtil;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class LuaScript {
    private final File file;
    private final String name;
    private final Globals globals;

    private boolean loaded = false;
    private String error = null;
    private long lastModified = 0L;

    public LuaScript(File file) {
        this.file = file;
        String fileName = file.getName();
        this.name = fileName.toLowerCase().endsWith(".lua")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        this.globals = JsePlatform.standardGlobals();
        // Scripts receive the explicit CrewX bridge only. Arbitrary Java, OS,
        // filesystem and package loading are removed from the global scope.
        String[] blocked = {"luajava", "io", "os", "debug", "package", "require", "dofile", "loadfile"};
        for (String global : blocked) {
            this.globals.set(global, LuaValue.NIL);
        }
    }

    public File getFile() {
        return this.file;
    }

    public String getName() {
        return this.name;
    }

    public Globals getGlobals() {
        return this.globals;
    }

    public boolean isLoaded() {
        return this.loaded;
    }

    public String getError() {
        return this.error;
    }

    public boolean hasChangedOnDisk() {
        return this.file.exists() && this.file.lastModified() != this.lastModified;
    }

    public boolean load() {
        this.loaded = false;
        this.error = null;
        try {
            byte[] bytes = Files.readAllBytes(this.file.toPath());
            String source = new String(bytes, StandardCharsets.UTF_8);
            this.lastModified = this.file.lastModified();

            LuaValue chunk = this.globals.load(source, this.name);
            chunk.call();
            this.loaded = true;
            return true;
        } catch (LuaError e) {
            this.error = e.getMessage();
            ChatUtil.sendFormatted("&cerror in &f" + this.name + "&c: " + this.error);
            return false;
        } catch (IOException e) {
            this.error = e.toString();
            ChatUtil.sendFormatted("&ccould not read &f" + this.name + "&c: " + this.error);
            return false;
        } catch (Throwable t) {
            this.error = t.toString();
            ChatUtil.sendFormatted("&cfailed to load &f" + this.name + "&c: " + this.error);
            return false;
        }
    }

    public LuaValue getFunction(String functionName) {
        LuaValue value = this.globals.get(functionName);
        return (value != null && value.isfunction()) ? value : null;
    }

    public boolean hasFunction(String functionName) {
        return this.getFunction(functionName) != null;
    }

    public Varargs call(String functionName, LuaValue... args) {
        LuaValue function = this.getFunction(functionName);
        if (function == null) {
            return LuaValue.NONE;
        }
        try {
            return function.invoke(LuaValue.varargsOf(args));
        } catch (LuaError e) {
            this.reportRuntimeError(functionName, e.getMessage());
            return LuaValue.NONE;
        } catch (Throwable t) {
            this.reportRuntimeError(functionName, String.valueOf(t));
            return LuaValue.NONE;
        }
    }

    public boolean callAllowing(String functionName, LuaValue... args) {
        LuaValue function = this.getFunction(functionName);
        if (function == null) {
            return true;
        }
        Varargs result = this.call(functionName, args);
        if (result == null || result.narg() == 0) {
            return true;
        }
        LuaValue first = result.arg1();
        return !first.isboolean() || first.toboolean();
    }

    private int errorsReported = 0;

    private void reportRuntimeError(String functionName, String message) {
        this.error = message;
        if (this.errorsReported < 3) {
            this.errorsReported++;
            ChatUtil.sendFormatted("&c" + this.name + "." + functionName + "&7: &f" + message);
            if (this.errorsReported == 3) {
                ChatUtil.sendFormatted("&7(silencing errors from &f" + this.name + "&7)");
            }
        }
    }

    public void resetErrorCount() {
        this.errorsReported = 0;
    }
}
