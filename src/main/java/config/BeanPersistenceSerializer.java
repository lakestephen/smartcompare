package config;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

/**
 * Created by IntelliJ IDEA.
 * User: Nick Ebbutt
 * Date: 29-Apr-2010
 * Time: 16:55:29
 */
public class BeanPersistenceSerializer implements ConfigSerializer {

    public String serialize(Object configObject) throws UnsupportedEncodingException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
        XMLEncoder encoder = new XMLEncoder(bos);
        encoder.writeObject(configObject);
        encoder.flush();
        encoder.close();
        return bos.toString("UTF-8");
    }

    public Object deserialize(String serializedConfig) throws UnsupportedEncodingException {
        ByteArrayInputStream bis = new ByteArrayInputStream(serializedConfig.getBytes("UTF-8"));
        XMLDecoder d = new XMLDecoder(bis);
        return d.readObject();
    }
}
