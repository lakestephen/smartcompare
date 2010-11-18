package config;

/**
 * Created by IntelliJ IDEA.
* User: Nick Ebbutt
* Date: 29-Apr-2010
* Time: 14:38:26
*/
public interface ConfigMigrationStategy {

    public String migrate(String configKey, String source);

}
