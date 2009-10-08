import junit.framework.TestCase;

/**
 * Created by IntelliJ IDEA.
 * User: Nick Ebbutt
 * Date: 06-Oct-2009
 * Time: 19:09:29
 */
public class TestCategoryExample extends TestCase {

    private StringBuilder sb = new StringBuilder();
    public Category cars;
    public Category vw;
    public Category ford;
    public Category focus;
    public Category focus2;
    public Category fiesta;
    public Category golf;

    class Category {
        String name;
        int priority;
        Category parent;

        Category(String name, int priority, Category parent) {
            this.name = name;
            this.priority = priority;
            this.parent = parent;
        }

        public String toString() {
            return name;
        }
    }

    public void setUp() {
        sb.setLength(0);
        cars = new Category("cars", 1, null);
        vw = new Category("vw", 1, cars);
        ford = new Category("ford", 2, cars);
        focus = new Category("focus", 1, ford);
        focus2 = new Category("focus", 2, ford);
        fiesta = new Category("fiesta", 1, ford);
        golf = new Category("golf", 2, vw);
    }

    public void testCategoryWithParentComparison() {
        //default to comparing parent by equality() so we miss the difference in priority between ford and vw
        SmartCompare sc = new SmartCompare("car1", "car2");
        sc.printDifferences(golf, fiesta, sb);
        assertEquals(
            "name->car1:[golf] car2:[fiesta]\n" +
            "priority->car1:[2] car2:[1]\n" +
            "parent->car1:[vw] car2:[ford]",
            sb.toString()
        );
    }

    public void testCategoryWithParentIntrospection() {
        //compare parent by introspection so we capture the difference in priority between ford and vw
        SmartCompare sc = new SmartCompare("car1", "car2").introspectPaths(".*parent");
        sc.printDifferences(golf, fiesta, sb);
        assertEquals(
            "name->car1:[golf] car2:[fiesta]\n" +
            "priority->car1:[2] car2:[1]\n" +
            "parent.name->car1:[vw] car2:[ford]\n" +
            "parent.priority->car1:[1] car2:[2]",
            sb.toString()
        );
    }

    public void testCategoryWithoutPriority() {
        SmartCompare sc = new SmartCompare("car1", "car2").introspectPaths(".*parent").ignorePaths(".*priority");
        sc.printDifferences(golf, fiesta, sb);
        assertEquals(
            "name->car1:[golf] car2:[fiesta]\n" +
            "parent.name->car1:[vw] car2:[ford]",
            sb.toString()
        );

        sb.setLength(0);
        sc = new SmartCompare("car1", "car2").introspectPaths(".*parent").ignorePaths(".*priority");
        sc.printDifferences(focus, focus2, sb);
        assertEquals(
            "",
            sb.toString()
        );
    }

}
