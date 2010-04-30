package config;

/**
 * Created by IntelliJ IDEA.
* User: Nick Ebbutt
* Date: 29-Apr-2010
* Time: 14:35:47
*/
class ConfigFileKey {
    private String name;
    private long version;

    public ConfigFileKey(String name, long version) {
        this.name = name;
        this.version = version;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConfigFileKey)) return false;

        final ConfigFileKey configFileKey = (ConfigFileKey) o;

        if (version != configFileKey.version) return false;
        if (!name.equals(configFileKey.name)) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = name.hashCode();
        result = 29 * result + (int) (version ^ (version >>> 32));
        return result;
    }

    public String toString() {
        return name + "/" + version;
    }
}
