import junit.framework.TestCase;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: Nick Ebbutt
 * Date: 30-Jul-2009
 * Time: 15:03:06
 */
public class DemoFieldDiffererenceCalculator extends TestCase {

    private StringBuilder sb = new StringBuilder();
    public FieldDifferenceCalculator f;

    public void setUp() {
        sb.setLength(0);
        f = new FieldDifferenceCalculator("record1", "record2");
    }

    public void testSimpleDifference() {

        class Recording {
            int lengthInMinutes;

            public Recording(int lengthInMinutes) {
                this.lengthInMinutes = lengthInMinutes;
            }
        }

        Recording recording1 = new Recording(10);
        Recording recording2 = new Recording(20);

        f.printDifferences(recording1, recording2, sb);
        assertEquals("lengthInMinutes->record1:[10] record2:[20]\n", sb.toString());
    }


    public void testArrays() {

        class Recording {
            int lengthInMinutes;
            String[] artistNames;

            Recording(int lengthInMinutes, String[] artistNames) {
                this.lengthInMinutes = lengthInMinutes;
                this.artistNames = artistNames;
            }
        }

        Recording recording1 = new Recording(10, new String[] { "Elton John" });
        Recording recording2 = new Recording(20, new String[] { "Elton John" });
        f.introspectPath("artistNames");

        f.printDifferences(recording1, recording2, sb);
        assertEquals("lengthInMinutes->record1:[10] record2:[20]", sb.toString());

        sb.setLength(0);
        recording2.artistNames = new String[] { "Elvis Presley" };
        f.printDifferences(recording1, recording2, sb);
        assertEquals(
            "lengthInMinutes->record1:[10] record2:[20]\n" +
            "artistNames.0->record1:[Elton John] record2:[Elvis Presley]",
            sb.toString()
        );


        sb.setLength(0);
        recording2.artistNames = new String[] { "Elvis Presley", "Eric Clapton" };
        f.printDifferences(recording1, recording2, sb);
        assertEquals(
            "lengthInMinutes->record1:[10] record2:[20]\n" +
            "artistNames.0->record1:[Elton John] record2:[Elvis Presley]\n" +
            "artistNames.1->record1:[Undefined Field] record2:[Eric Clapton]",
            sb.toString()
        );

    }

}
