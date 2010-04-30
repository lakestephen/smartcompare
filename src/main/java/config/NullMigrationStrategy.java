package config;

/**
 *
 */
public class NullMigrationStrategy implements ConfigMigrationStategy {

    private long versionTarget;

    /**
     * For construction by Reflection.
     * @param versionTarget
     * @param arguments
     */
    public NullMigrationStrategy(long versionTarget, String[] arguments) {
        this.versionTarget = versionTarget;
    }

    public String migrate(String configKey, String source) {
        LogMethods.log.info("Migrating " + configKey + " configuration to version " + versionTarget + " using null strategy");
        return source;
    }
}
