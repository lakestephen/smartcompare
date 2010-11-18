package config;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
* User: Nick Ebbutt
* Date: 29-Apr-2010
* Time: 14:35:19
* 
* Interface to implement for classes which are responsible for loading configurations
*/
public interface ConfigSource {

    /**
     * Return the most up to date ConfigData available
     * If this is not the most recent version, it may subsequently be migrated
     *
     * @param configName, name of the config to load
     * @param supportedVersions, sorted list of versions, lowest (earliest) first
     * @return the ConfigData with the highest available version id, pre migration
     */
    public ConfigData loadConfiguration(String configName, List<Long> supportedVersions) throws ConfigManagerException;
}
