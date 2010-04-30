package config;

/**
 * Created by IntelliJ IDEA.
 * User: Nick Ebbutt
 * Date: 29-Apr-2010
 * Time: 14:36:21
 */
public interface LogMethods {

    public static LogMethods log = new DefaultLogMethods();

    void info(String s);

    void error(String description, Throwable cause);

    void error(String description);

    void debug(String s);

    void warn(String s);


    static class DefaultLogMethods implements LogMethods {

        public void info(String s) {
            System.out.println("Configuration --> INFO " + s);
        }

        public void error(String description, Throwable cause) {
            System.err.println("Configuration --> ERROR " + description + " " + cause);
            cause.printStackTrace();
        }

        public void error(String description) {
            System.err.println("Configuration --> ERROR " + description);
        }

        public void debug(String s) {
            System.out.println("Configuration --> DEBUG " + s);
        }

        public void warn(String s) {
            System.out.println("Configuration --> WARN " + s);
        }
    }
}