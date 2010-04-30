package config;

import java.net.URL;
import java.util.*;
import java.io.File;

/**
 *
 */
public class ConfigManager<V> {

    private ConfigSource configSource;
    private ConfigSink configSink;
    private MigrationSource migrationSource;
    private ConfigSerializer<V> serializer = new BeanPersistenceSerializer();

    public ConfigManager() {
        this(new ClasspathMigrationLoader());
        createDefaultSourceAndSink();
    }

    public ConfigManager(URL... migrationsResources) {
        migrationSource = new UrlMigrationLoader(Arrays.asList(migrationsResources));
        createDefaultSourceAndSink();
    }

    public ConfigManager(MigrationSource migrationSource) {
        this.migrationSource = migrationSource;
        createDefaultSourceAndSink();
    }

    public ConfigManager(File configDirectory) {
        this();
        createDefaultSourceAndSink(configDirectory);
    }

    public V loadConfig(String configName) throws ConfigManagerException {
        try {
            return doLoad(configName);
        } catch (ConfigManagerException t) {
            throw t;
        } catch (Throwable t) {
            throw new ConfigManagerException("Failed during ConfigManger.loadConfig", t);
        }
    }

    public void saveConfig(String configName, V config) throws ConfigManagerException {
        try {
            doSave(configName, config);
        } catch (ConfigManagerException t) {
            throw t;
        } catch (Throwable t) {
            throw new ConfigManagerException("Failed during ConfigManger.saveConfig", t);
        }
    }

    public void setConfigSource(ConfigSource configSource) {
        this.configSource = configSource;
    }

    public void setConfigSink(ConfigSink configSink) {
        this.configSink = configSink;
    }

    public void setSerializer(ConfigSerializer<V> serializer) {
        this.serializer = serializer;
    }

    public void setMigrationSource(MigrationSource migrationSource) {
        this.migrationSource = migrationSource;
    }

    public void setConfigDirectory(File directory) {
        createDefaultSourceAndSink(directory);
    }

    private void createDefaultSourceAndSink() {
        createDefaultSourceAndSink(new File(System.getProperty("user.home")));
    }

    private void createDefaultSourceAndSink(File configDirectory) {
        FileSourceAndSink defaultSourceAndSink = new FileSourceAndSink(configDirectory);
        configSource = defaultSourceAndSink;
        configSink = defaultSourceAndSink;
    }

    private V doLoad(String configName) throws Exception {
        SortedMap<Long, List<ConfigMigrationStategy>> configMigrations = readConfigMigrations();
        ConfigData d = configSource.loadConfiguration(configName, new ArrayList<Long>(configMigrations.keySet()));
        d = patchConfig(configMigrations, d);
        String serializedConfig = d.getSerializedConfig();
        return serializer.deserialize(serializedConfig);
    }

    private void doSave(String configName, V config) throws Exception {
        SortedMap<Long, List<ConfigMigrationStategy>> configMigrations = readConfigMigrations();
        String serializedConfig = serializer.serialize(config);
        ConfigData configData = new ConfigData(configName, configMigrations.lastKey(), serializedConfig);
        configSink.saveConfiguration(configData);
    }

    private SortedMap<Long, List<ConfigMigrationStategy>> readConfigMigrations() throws Exception {
        SortedMap<Long, List<ConfigMigrationStategy>> configMigrations;
        configMigrations = migrationSource.loadConfigMigrations();
        if ( configMigrations.size() == 0) {
            throw new ConfigManagerException("No config migrations defined, we cannot determine an expected version");
        }
        return configMigrations;
    }


    private ConfigData patchConfig(SortedMap<Long,List<ConfigMigrationStategy>> migrations, ConfigData oldConfig) {
        SortedMap<Long,List<ConfigMigrationStategy>> migrationsToRun =
                migrations.tailMap(oldConfig.getVersion()); // this is inclusive of the fromVersion

        String configName = oldConfig.getConfigName();
        long fromVersion = oldConfig.getVersion();
        long toVersion = migrations.lastKey();
        String configString = oldConfig.getSerializedConfig();

        for (Map.Entry<Long, List<ConfigMigrationStategy>> entry : migrationsToRun.entrySet() ) {
            for (ConfigMigrationStategy s : entry.getValue()) {
                LogMethods.log.info("Migrating config " + configName + " to version " + entry.getKey() + " using strategy " + s);
                configString = s.migrate(configName, configString);
            }
        }
        LogMethods.log.info("Patched " + configName + " from " + fromVersion + " to " + toVersion);
        return new ConfigData(configName, toVersion, configString);
    }
}
