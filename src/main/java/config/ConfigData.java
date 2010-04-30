package config;

/**
 * Created by IntelliJ IDEA.
 * User: Nick Ebbutt
 * Date: 29-Apr-2010
 * Time: 16:07:25
 */
public class ConfigData {

    private long version;
    private String configName;
    private String savedConfig;

    public ConfigData() {}

    public ConfigData(String configName, long version, String savedConfig) {
        this.configName = configName;
        this.version = version;
        this.savedConfig = savedConfig;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public String getSerializedConfig() {
        return savedConfig;
    }

    public void setSavedConfig(String savedConfig) {
        this.savedConfig = savedConfig;
    }
}
