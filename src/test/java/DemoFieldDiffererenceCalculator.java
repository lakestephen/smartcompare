import junit.framework.TestCase;

import java.util.*;

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
            int length;

            public Recording(int length) {
                this.length = length;
            }
        }

        Recording recording1 = new Recording(10);
        Recording recording2 = new Recording(20);

        f.printDifferences(recording1, recording2, sb);
        assertEquals("length->record1:[10] record2:[20]", sb.toString());
    }


    public void testArrays() {

        class Recording {
            private String[] artistNames;
            private int length;

            public Recording setLength(int length) {
                this.length = length;
                return this;
            }

            public Recording setArtistNames(String... artistNames) {
                this.artistNames = artistNames;
                return this;
            }
        }

        Recording recording1 = new Recording().setLength(10).setArtistNames("Elton John");
        Recording recording2 = new Recording().setLength(20).setArtistNames("Elton John");
        f.printDifferences(recording1, recording2, sb);
        assertEquals("length->record1:[10] record2:[20]", sb.toString());

        sb.setLength(0);
        recording2.setArtistNames("Elvis Presley");
        f.printDifferences(recording1, recording2, sb);
        assertEquals(
            "length->record1:[10] record2:[20]\n" +
            "artistNames.0->record1:[Elton John] record2:[Undefined]\n" +
            "artistNames.1->record1:[Undefined] record2:[Elvis Presley]",
            sb.toString()
        );


        sb.setLength(0);
        recording2.setArtistNames("Elvis Presley", "Eric Clapton");
        f.printDifferences(recording1, recording2, sb);
        assertEquals(
            "length->record1:[10] record2:[20]\n" +
            "artistNames.0->record1:[Elton John] record2:[Undefined]\n" +
            "artistNames.1->record1:[Undefined] record2:[Elvis Presley]\n" +
            "artistNames.2->record1:[Undefined] record2:[Eric Clapton]",
            sb.toString()
        );

    }

    public void testMaps() {
        class Recording {
            private Map<String,String> players = new TreeMap<String,String>();
            private int length;
            private String[] artistNames;

            public Recording setLength(int length) {
                this.length = length;
                return this;
            }

            public Recording setArtistNames(String... artistNames) {
                this.artistNames = artistNames;
                return this;
            }

            public Recording addPlayer(String instrument, String name) {
                players.put(instrument, name);
                return this;
            }
        }

        Recording recording1 = new Recording().setLength(10).setArtistNames("Elton John");
        Recording recording2 = new Recording().setLength(20).setArtistNames("Elton John");
        recording1.addPlayer("guitar", "Eric Clapton");
        recording2.addPlayer("guitar", "Mark Knopfler");
        recording1.addPlayer("castanets", "Boris Johnson");
        recording2.addPlayer("castanets", "Boris Johnson");
        recording1.addPlayer("tuba", "Screaming Lord Such");

        sb.setLength(0);
        f.printDifferences(recording1, recording2, sb);
        assertEquals(
            "length->record1:[10] record2:[20]\n" +
            "players.guitar->record1:[Eric Clapton] record2:[Mark Knopfler]\n" +
            "players.tuba->record1:[Screaming Lord Such] record2:[Undefined]",
            sb.toString()
        );
    }

    public void testObjectIntrospection() {
        class StringQuartet {
            private String viola;
            private String cello;
            private List<String> violins;
            boolean willPlayforFood;

            public StringQuartet(String viola, String cello, List<String> violins, boolean willPlayforFood) {
                this.viola = viola;
                this.cello = cello;
                this.violins = violins;
                this.willPlayforFood = willPlayforFood;
            }
        }

        class Recording {
            private int length;
            private String[] artistNames;
            private Map<String,String> players = new TreeMap<String,String>();
            private StringQuartet stringQuartet;

            public Recording setLength(int length) {
                this.length = length;
                return this;
            }

            public Recording setArtistNames(String... artistNames) {
                this.artistNames = artistNames;
                return this;
            }

            public Recording addPlayer(String instrument, String name) {
                players.put(instrument, name);
                return this;
            }

            public Recording setStringQuartet(StringQuartet s) {
                stringQuartet = s;
                return this;
            }
        }

        Recording recording1 = new Recording().setLength(10).setArtistNames("Elton John");
        Recording recording2 = new Recording().setLength(20).setArtistNames("Elton John");

        recording1.addPlayer("guitar", "Eric Clapton").addPlayer("castanets", "Boris Johnson");
        recording2.addPlayer("guitar", "Mark Knopfler").addPlayer("castanets", "Boris Johnson");

        recording1.setStringQuartet(new StringQuartet("Mr. Viola", "Mr. Cello", Arrays.asList("Isaac Perlman", "Nigel Kennedy"), false));
        recording2.setStringQuartet(new StringQuartet("Mr. Viola", "Mr. Cello", Arrays.asList("Isaac Perlman", "Heifitz"), true));

        //tell the difference calculator we want to introspect quartet to pick
        //up the differences by field rather than just compare it
        f.introspectPaths("stringQuartet");        

        sb.setLength(0);
        f.printDifferences(recording1, recording2, sb);
        assertEquals(
            "length->record1:[10] record2:[20]\n" +
            "players.guitar->record1:[Eric Clapton] record2:[Mark Knopfler]\n" +
            "stringQuartet.willPlayforFood->record1:[false] record2:[true]\n" +
            "stringQuartet.violins.1->record1:[Nigel Kennedy] record2:[Undefined]\n" +
            "stringQuartet.violins.2->record1:[Undefined] record2:[Heifitz]",
            sb.toString()
        );
    }

}
