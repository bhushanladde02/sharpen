package io.sharpen.domain;

/** Small enums kept together; each is stored as a string column so the database stays readable. */
public final class Enums {

    private Enums() {}

    public enum AccountType { INDIVIDUAL, COMPANY }

    public enum UsageContext {
        PERSONAL("Personal"), PROFESSIONAL("Professional");
        public final String label;
        UsageContext(String label) { this.label = label; }
    }

    public enum TaskCategory {
        CODING("Coding"), WRITING("Writing"), RESEARCH("Research"), ANALYSIS("Data & analysis"),
        LEARNING("Learning"), PLANNING("Planning"), CREATIVE("Creative"), ADMIN("Admin & email"), OTHER("Other");
        public final String label;
        TaskCategory(String label) { this.label = label; }
    }

    public enum SessionSource {
        MANUAL("Logged by you"), EXTENSION("Browser extension"), API_IMPORT("Usage import");
        public final String label;
        SessionSource(String label) { this.label = label; }
    }
}
