package config;

import java.util.List;
import java.util.SortedMap;

/**
 * Created by IntelliJ IDEA.
 * User: Nick Ebbutt
 * Date: 29-Apr-2010
 * Time: 16:48:13
 */
public interface MigrationSource {

    SortedMap<Long, List<ConfigMigrationStategy>> loadConfigMigrations() throws Exception;
}
