package config;

/**
 * Created by IntelliJ IDEA.
 * User: Nick Ebbutt
 * Date: 29-Apr-2010
 * Time: 16:24:56
 */
public interface ConfigSerializer<V> {

    String serialize(V configObject) throws Exception;

    V deserialize(String serializedConfig) throws Exception;
}
