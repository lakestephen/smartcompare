package config;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
* User: Nick Ebbutt
* Date: 29-Apr-2010
* Time: 14:35:32
*/
public interface ConfigSink {
    public void saveConfiguration(ConfigData configuration) throws ConfigManagerException;
}
