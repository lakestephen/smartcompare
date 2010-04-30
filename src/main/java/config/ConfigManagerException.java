package config;

/**
 * Created by IntelliJ IDEA.
 * User: Nick Ebbutt
 * Date: 29-Apr-2010
 * Time: 15:31:09
 */
public class ConfigManagerException extends Exception {

    public ConfigManagerException(String message) {
        super(message);
    }

    public ConfigManagerException(String message, Throwable t) {
        super(message, t);
    }
}
