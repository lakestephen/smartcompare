package config;

import java.util.regex.Pattern;

/**
 * A configuration migration strategy that implements basic regex find and replace.  The power of this is limited
 * by the power of the Java Regex, but bear in mind that some quite complicated things can be accomplished such
 * as back-references.  e.g. You look for "(H\w*), (W\w*)" and replace with "$2, $1" which would find all occurances
 * of something like "Hello, World" and change it to "World, Hello".
 *
 * @author Brendon McLean
 * @version $Revision: 1.1 $
 */

public class RegexMigrationStrategy implements ConfigMigrationStategy {

    private static final int FIND_PATTERN_ARG = 0;
    private static final int REPLACE_STRING_ARG = 1;

    private long versionTarget;
    private Pattern findPattern;
    private String replaceString;

    public RegexMigrationStrategy(long versionTarget, String[] arguments) {
        this.versionTarget = versionTarget;
        this.findPattern = Pattern.compile(arguments[FIND_PATTERN_ARG]);
        this.replaceString = arguments[REPLACE_STRING_ARG];
    }

    public String migrate(String configKey, String source) {
        LogMethods.log.info("Migrating " + configKey + " configuration to version " + versionTarget + " using regular expression strategy");
        return findPattern.matcher(source).replaceAll(replaceString);
    }
}
