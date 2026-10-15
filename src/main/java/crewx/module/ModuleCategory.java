package crewx.module;

import java.util.Locale;

public enum ModuleCategory {
    COMBAT("combat", "Combat"),
    MOVEMENT("movement", "Movement"),
    RENDER("render", "Render"),
    PLAYER("player", "Player"),
    MISC("misc", "Misc"),
    SCRIPT("script", "Script");

    private static final ModuleCategory[] VALUES = values();
    private static final String MODULE_PACKAGE_PREFIX = "crewx.module.modules.";

    private final String folder;
    private final String displayName;

    ModuleCategory(String folder, String displayName) {
        this.folder = folder;
        this.displayName = displayName;
    }

    public String getFolder() {
        return this.folder;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public static ModuleCategory fromPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return MISC;
        }

        if (packageName.equals("crewx.script") || packageName.startsWith("crewx.script.")) {
            return SCRIPT;
        }

        if (packageName.startsWith(MODULE_PACKAGE_PREFIX)) {
            String subpackage = packageName.substring(MODULE_PACKAGE_PREFIX.length());
            int separator = subpackage.indexOf('.');
            String folder = separator < 0 ? subpackage : subpackage.substring(0, separator);
            String normalizedFolder = folder.toLowerCase(Locale.ROOT);
            for (ModuleCategory category : VALUES) {
                if (category.folder.equals(normalizedFolder)) {
                    return category;
                }
            }
        }

        return MISC;
    }
}
