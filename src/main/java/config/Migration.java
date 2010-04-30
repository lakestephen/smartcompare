package config;

/**
 * Created by IntelliJ IDEA.
* User: Nick Ebbutt
* Date: 29-Apr-2010
* Time: 14:38:49
*/
public final class Migration {
    private long versionTarget;
    private String migrationStrategyClassName;
    private String[] arguments;

    public long getVersionTarget() {
        return versionTarget;
    }

    public void setVersionTarget(long versionTarget) {
        this.versionTarget = versionTarget;
    }

    public String getMigrationStrategyClassName() {
        return migrationStrategyClassName;
    }

    public void setMigrationStrategyClassName(String migrationStrategyClassName) {
        this.migrationStrategyClassName = migrationStrategyClassName;
    }

    public String[] getArguments() {
        return arguments;
    }

    public void setArguments(String[] arguments) {
        this.arguments = arguments;
    }
}
