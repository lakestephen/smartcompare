package config;

import java.util.*;
import java.net.URL;
import java.io.InputStream;
import java.io.IOException;
import java.beans.XMLDecoder;

/**
 * Created by IntelliJ IDEA.
 * User: Nick Ebbutt
 * Date: 29-Apr-2010
 * Time: 14:41:09
 */
public class UrlMigrationLoader implements MigrationSource {

    private List<URL> urls;

    public UrlMigrationLoader(URL... url) {
        this(Arrays.asList(url));
    }

    public UrlMigrationLoader(List<URL> urls) {
        this.urls = urls;
    }

    public SortedMap<Long, List<ConfigMigrationStategy>> loadConfigMigrations() throws Exception {
        SortedMap<Long, List<ConfigMigrationStategy>> configMigrations = new TreeMap<Long, List<ConfigMigrationStategy>>();
        try {
            if ( urls.size() == 0) {
                throw new Exception("No configMigration resources found");
            }

            for( URL url : urls ) {
                readMigrations(configMigrations, url);
            }
        } catch (Exception e) {
            LogMethods.log.error("Failed to load config migrations", e);
            throw e;
        }
        return configMigrations;
    }

    private void readMigrations(SortedMap<Long, List<ConfigMigrationStategy>> configMigrations, URL url) throws IOException {
        InputStream i = null;
        try {
            i = url.openStream();
            XMLDecoder d = new XMLDecoder(i);
            Migrations migrations = (Migrations)d.readObject();

            for (Migration m : migrations.getMigrations()) {
                long versionTarget = m.getVersionTarget();
                String className = m.getMigrationStrategyClassName();
                String[] constructorArguments = m.getArguments();

                List<ConfigMigrationStategy> configMigrationForVersionTarget = configMigrations.get(versionTarget);
                if (configMigrationForVersionTarget == null) {
                    configMigrationForVersionTarget = new ArrayList();
                    configMigrations.put(versionTarget, configMigrationForVersionTarget);
                }

                ConfigMigrationStategy configMigration = createMigrationStrategy(versionTarget, className, constructorArguments);
                configMigrationForVersionTarget.add(configMigration);
            }
        } finally {
            try {
                if (i != null) i.close();
            } catch (IOException e) {
                LogMethods.log.error("Failed to close ConfigMigration InputStream from URL " + url, e);
            }
        }
    }


    private ConfigMigrationStategy createMigrationStrategy(Long versionTarget, String className, String[] constructorArguments) {
        try {
            Object[] arguments = new Object[]{versionTarget, constructorArguments};
            Class migrationClass = Class.forName(className);
            return (ConfigMigrationStategy) migrationClass.getConstructor(new Class[]{long.class, String[].class}).newInstance(arguments);
        } catch (Exception e) {
            throw new RuntimeException("Error loading strategy delegates for config migration", e);
        }
    }


}
