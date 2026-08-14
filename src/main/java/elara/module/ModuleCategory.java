package elara.module;

public enum ModuleCategory {
    COMBAT("Combat", 0),
    MOVEMENT("Movement", 1),
    RENDER("Render", 2),
    UTILITY("Utility", 3),
    WORLD("World", 4),
    EXPLOIT("Exploit", 5),
    MISC("Misc", 6);

    private final String name;
    private final int index;

    ModuleCategory(String name, int index) {
        this.name = name;
        this.index = index;
    }

    public String getName() {
        return this.name;
    }

    public int getIndex() {
        return this.index;
    }

    public static ModuleCategory fromIndex(int index) {
        for (ModuleCategory category : values()) {
            if (category.index == index) {
                return category;
            }
        }
        return MISC;
    }
}
