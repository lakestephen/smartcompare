import junit.framework.TestCase;

/**
 * Created by IntelliJ IDEA.
 * User: Nick Ebbutt
 * Date: 30-Jul-2009
 * Time: 15:03:06
 */
public class DemoFieldDiffererenceCalculator extends TestCase {

    public void testSimpleDifference() {

        class Shape {
            private int width;
            private int height;

            public Shape(int width, int height) {
                this.width = width;
                this.height = height;
            }
        }

        Shape s1 = new Shape(10, 10);
        Shape s2 = new Shape(5, 10);

        FieldDifferenceCalculator f = new FieldDifferenceCalculator("shape1", "shape2");
        f.printDifferences(s1, s2);
    }

}
