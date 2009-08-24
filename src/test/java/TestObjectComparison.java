import java.util.*;
import java.util.List;
import java.awt.*;

import junit.framework.TestCase;


/**
 * Created by IntelliJ IDEA.
 * User: Nick Ebbutt
 * Date: 14-Jul-2009
 * Time: 13:47:17
 */
public class TestObjectComparison extends TestCase {

    public List<ObjectComparison.Difference> differences;
    public Object t1;
    public Object t2;

    private final BeanFieldIntrospectingConfig beanInstrospectingConfig = new BeanFieldIntrospectingConfig();
    private final ObjectComparison.DefaultConfig defaultConfig = new ObjectComparison.DefaultConfig();
    private final SubclassIntrospectingConfig subclassConfig = new SubclassIntrospectingConfig();
    private final SuperclassIntrospectingConfig superclassConfig = new SuperclassIntrospectingConfig();

    public void testDefaultComparisonFields() {
        t1 = new TestFieldDifferenceBean(10d, "test");
        t2 = new TestFieldDifferenceBean(10d, "wibble");

        introspect(defaultConfig);
        checkDifferences(
            newValueDifference("stringField", "object1:[test] object2:[wibble]", "test", "wibble")
        );

        t1 = new TestFieldDifferenceBean(10d, "test");
        t2 = new TestFieldDifferenceBean(5d, "wibble");
        introspect(defaultConfig);

        checkDifferences(
            newValueDifference("doubleField", "object1:[10.0] object2:[5.0]", 10d, 5d),
            newValueDifference("stringField", "object1:[test] object2:[wibble]", "test", "wibble")
        );

    }

    public void testObjectDescriptions() {
        t1 = new TestFieldDifferenceBean(10d, "test");
        t2 = new TestFieldDifferenceBean(10d, "wibble");

        introspect("description1", "description2", defaultConfig);
        checkDifferences(
            newValueDifference("stringField", "description1:[test] description2:[wibble]", "test", "wibble")
        );
    }

    public void testNullsAsPrimaryInputs() {
        t1 = new TestFieldDifferenceBean(10d, "test");
        t2 = null;
        introspect(defaultConfig);
        checkDifferences(
            newValueDifference(
                ObjectComparison.INPUT_OBJECT_TEXT,
                "object1:[TestFieldDifferenceBean{doubleField=10.0, stringField='test', colorField=null}] object2:[null]",
                t1,
                t2
            )
        );

        t1 = null;
        t2 = new TestFieldDifferenceBean(10d, "test");
        introspect(defaultConfig);
        checkDifferences(
            newValueDifference(
                ObjectComparison.INPUT_OBJECT_TEXT,
                "object1:[null] object2:[TestFieldDifferenceBean{doubleField=10.0, stringField='test', colorField=null}]",
                t1,
                t2
            )
        );

        t1 = null;
        t2 = null;
        introspect(defaultConfig);
        checkDifferences();
    }

    public void testNullFieldComparison() {
        t1 = new TestFieldDifferenceBean(10d, "test");
        t2 = new TestFieldDifferenceBean(10d, null);
        introspect(defaultConfig);
        checkDifferences(
            newValueDifference(
                "stringField",
                "object1:[test] object2:[null]",
                "test",
                null
            )
        );

        t1 = new TestFieldDifferenceBean(10d, null);
        t2 = new TestFieldDifferenceBean(10d, "test");
        introspect(defaultConfig);
        checkDifferences(
            newValueDifference(
                "stringField",
                "object1:[null] object2:[test]",
                null,
                "test")
        );

    }

    public void testClassDifferencesAsPrimaryInputs() {
        t1 = "wibble";
        t2 =  new TestFieldDifferenceBean(10d, "test");

        //introspect ignoring differences in class fields
        introspect(new ObjectComparison.DefaultConfig() {
            public ObjectComparison.FieldIntrospector getFieldIntrospector(String fieldPath, Class commonSuperclass, Object o1, Object o2) {
                return new ObjectComparison.SuperclassFieldIntrospector();
            }
        });

        checkDifferences(
            newClassDifference(
                ObjectComparison.INPUT_OBJECT_TEXT,
                "different class type: object1: [java.lang.String] object2: [" + TestFieldDifferenceBean.class.getName() + "]",
                t1.getClass(),
                t2.getClass())
        );
    }

    public void testDifferentSubclassAsField() {
        TestFieldDifferenceBean child1 = new TestFieldDifferenceBean(10d, "test");
        TestBeanSubclass1 child2 = new TestBeanSubclass1(10d, "test", 10, null);
        t1 = new TestFieldDifferenceBean(10d, "test", child1);
        t2 = new TestFieldDifferenceBean(10d, "test", child2);

        introspect(superclassConfig);
        checkDifferences(
            newClassDifference(
                "beanField",
                "different class type: object1: [" + TestFieldDifferenceBean.class.getName() + "] object2: [" + TestBeanSubclass1.class.getName() + "]",
                child1.getClass(),
                child2.getClass())
        );
    }

    public void testIsComparisonField() {
        t1 = new TestFieldDifferenceBean(10d, "test", Color.RED);
        t2 = new TestFieldDifferenceBean(10d, "test", Color.BLACK);

        //this config suppresses comparison for Color fields so we should get no differences
        introspect(new ObjectComparison.DefaultConfig() {
            public ObjectComparison.ComparisonFieldType getComparisonFieldType(ObjectComparison.Field f) {
                return f.getType() == Color.class ?
                        ObjectComparison.ComparisonFieldType.IGNORE_FIELD :
                        super.getComparisonFieldType(f);
            }

        });
        checkDifferences();

        introspect(defaultConfig);
        //now the color difference should be detected
        checkDifferences(
            newValueDifference(
                "colorField",
                "object1:[java.awt.Color[r=255,g=0,b=0]] object2:[java.awt.Color[r=0,g=0,b=0]]",
                Color.RED,
                Color.BLACK
            )
        );
    }

    public void testGetComparator() {
        t1 = new TestFieldDifferenceBean(10d, "test");
        t2 = new TestFieldDifferenceBean(9d, "test");

        introspect(defaultConfig);
        checkDifferences(
            newValueDifference(
                "doubleField",
                "object1:[10.0] object2:[9.0]",
                10d,
                9d
            )
        );

        //now use a comparator which ignores small differences in doubles
        introspect(new ObjectComparison.DefaultConfig() {
            public ObjectComparison.EqualityComparator getComparator(ObjectComparison.Field f) {
                ObjectComparison.EqualityComparator result = null;
                if ( f.getType().getName().equals("double")) {
                    return new ObjectComparison.EqualityComparator<Double>() {
                        public boolean isEqual(Double o1, Double o2) {
                            return Math.abs(o1-o2) < 2;
                        }
                    };
                }
                return result;
                }
            });
        checkDifferences();
    }

    public void testChildFieldIntrospection() {

        TestFieldDifferenceBean child1 = new TestFieldDifferenceBean(1d, "test1");
        TestFieldDifferenceBean child2 = new TestFieldDifferenceBean(1d, "test2");
        t1 = new TestFieldDifferenceBean(9d, "test", child1);
        t2 = new TestFieldDifferenceBean(9d, "test", child2);

        introspect(beanInstrospectingConfig);
        checkDifferences(
            newValueDifference(
                "stringField",
                "object1:[test1] object2:[test2]",
                "test1",
                "test2",
                "beanField"
            )
        );

        t1 = new TestFieldDifferenceBean(9d, "test", child1);
        t2 = new TestFieldDifferenceBean(9d, "test", child1);
        introspect(beanInstrospectingConfig);
        checkDifferences();
    }

    public void testSuperclassFieldDifference() {
        t1 = new TestBeanSubclass1(10d, "test", 10, null);
        t2 = new TestBeanSubclass1(10d, "test2", 100, null);

        introspect(defaultConfig);
        checkDifferences(
            newValueDifference(
                "intField",
                "object1:[10] object2:[100]",
                Integer.valueOf(10),
                Integer.valueOf(100)
            ),
            newValueDifference(
                "stringField",
                "object1:[test] object2:[test2]",
                "test",
                "test2"
            )
        );
    }

    public void testMapFieldDifference() {
        Map<String, String> map1 = new TreeMap<String,String>();
        Map<String, String> map2 = new TreeMap<String,String>();

        map1.put("key1", "test");
        map2.put("key1", "wibble");
        map1.put("key2", "test");
        map2.put("key2", "test");
        map1.put("key3", "test");
        map2.put("key3", "wibble");

        t1 = map1;
        t2 = map2;
        introspect();
        checkDifferences(
            newValueDifference(
                "key1",
                "object1:[test] object2:[wibble]",
                "test",
                "wibble"
            ),
            newValueDifference(
                "key3",
                "object1:[test] object2:[wibble]",
                "test",
                "wibble"
            )
        );

        t1 = new TestMapBean(map1);
        t2 = new TestMapBean(map2);
        introspect();
        checkDifferences(
            newValueDifference(
                "key1",
                "object1:[test] object2:[wibble]",
                "test",
                "wibble",
                "mapField"
            ),
            newValueDifference(
                "key3",
                "object1:[test] object2:[wibble]",
                "test",
                "wibble",
                "mapField"
            )
        );

        map2.remove("key3");
        introspect();
        checkDifferences(
            newValueDifference(
                "key1",
                "object1:[test] object2:[wibble]",
                "test",
                "wibble",
                "mapField"
            ),
            newFieldDifference(
                "key3",
                "object1:[test] object2:[Undefined]",
                "test",
                ObjectComparison.Field.UNDEFINED_FIELD_VALUE,
                "mapField"
            )
        );
    }

    public void testMapFieldWithDifferingKeyAndValueClasses() {
        HashMap<Object, Object> map1 = new HashMap<Object,Object>();
        HashMap<Object, Object> map2 = new HashMap<Object,Object>();

        //it doesn't matter if the keys are different class types
        //the superset of unique Object keys in the maps identify the 'fields', this set may contain different class types

        //for the values, the type of the 'field' generated by the MapFieldIntrospector will be the first common superclass
        //of the values in the map, if the values differ in class
        //eg.
        //-if the value for key1 is String.class and in map1, and String.class in map3, String.class will be the field type
        //-if the value for key1 is Shape.class in map1 and Square.class in map2, where Square sublcasses Shape, then Shape.class should be the type

        //If we try to introspect further down the bean tree using values of different class types
        //then we should get a 'different class types' difference returned, and introspection cannot continue

        //However, it should be possible to configure a comparator for the field comparison which can accept objects
        //of either class type, which should allow us to compare fields, even if the values are different class types

        map1.put("key1", "test");
        map2.put("key1", "wibble");
        map1.put(2, new TestFieldDifferenceBean(10d, "test"));
        map2.put(2, new TestBeanSubclass1(10d, "test", 10, null));

        t1 = map1;
        t2 = map2;
        introspect(superclassConfig);
        checkDifferences(
            newValueDifference(
                "key1",
                "object1:[test] object2:[wibble]",
                "test",
                "wibble"
            ),
            //test beans are introspection types, which means we try to introspect the field values
            //when we try to introspect the values we find they are different class types, so we get a class type difference
            //for the '2' field
            newClassDifference(
                "2",
                "different class type: object1: [" + TestFieldDifferenceBean.class.getName() + "] object2: [" + TestBeanSubclass1.class.getName() + "]",
                TestFieldDifferenceBean.class,
                TestBeanSubclass1.class
            )
        );
    }

    public void testSubclassIntrospection() {
        t1 = new TestBeanSubclass1(10d, "test", 100, null);
        t2 = new TestBeanSubclass2(10d, "test2", 10, null);

        introspect(subclassConfig);

        //the int fields are on the subclass, so are considered different fields for analysis even though they have
        //the same fieldName
        checkDifferences(
            newClassDifference(
                ObjectComparison.INPUT_OBJECT_TEXT,
                "different class type: object1: [" + TestBeanSubclass1.class.getName() + "] object2: [" + TestBeanSubclass2.class.getName() + "]",
                TestBeanSubclass1.class,
                TestBeanSubclass2.class
            ),
            newValueDifference(
                "stringField",
                "object1:[test] object2:[test2]",
                "test",
                "test2"
            ),
            newFieldDifference(
                "intField",
                "object1:[100] object2:[Undefined]",
                100,
                ObjectComparison.Field.UNDEFINED_FIELD_VALUE
            ),
            newFieldDifference(
                "intField",
                "object1:[Undefined] object2:[10]",
                ObjectComparison.Field.UNDEFINED_FIELD_VALUE,
                10
            ),
            newFieldDifference(
                "floatField",
                "object1:[null] object2:[Undefined]",
                null,
                ObjectComparison.Field.UNDEFINED_FIELD_VALUE
            ),
            newFieldDifference(
                "listField",
                "object1:[Undefined] object2:[null]",
                ObjectComparison.Field.UNDEFINED_FIELD_VALUE,
                null
            )
        );
    }

    //this is the same as the subclass introspector, but we ignore all fields from subclasses for the comparison
    //we only compare fields from the most specific shared superclass upwards
    public void testSuperclassIntrospection() {
        t1 = new TestBeanSubclass1(10d, "test", 100, null);
        t2 = new TestBeanSubclass2(10d, "test2", 10, null);

        introspect(superclassConfig);

        //the int fields are on the subclass, so are considered different fields for analysis even though they have
        //the same fieldName
        checkDifferences(
            newClassDifference(
                ObjectComparison.INPUT_OBJECT_TEXT,
                "different class type: object1: [" + TestBeanSubclass1.class.getName() + "] object2: [" + TestBeanSubclass2.class.getName() + "]",
                TestBeanSubclass1.class,
                TestBeanSubclass2.class
            ),
            newValueDifference(
                "stringField",
                "object1:[test] object2:[test2]",
                "test",
                "test2"
            )
        );
    }

    public void testFieldPaths() {
        t1 = new TestFieldDifferenceBean(10d, "test", new TestFieldDifferenceBean(20d, "test2"));
        t2 = new TestFieldDifferenceBean(10d, "test", new TestFieldDifferenceBean(20d, "test2"));

        final Set<String> expectedPaths = new HashSet<String>();
        expectedPaths.add("doubleField");
        expectedPaths.add("stringField");
        expectedPaths.add("beanField");
        expectedPaths.add("beanField.doubleField");
        expectedPaths.add("beanField.stringField");
        expectedPaths.add("beanField.beanField");

        class FieldPathCheckingConfig extends BeanFieldIntrospectingConfig {
            public ObjectComparison.ComparisonFieldType getComparisonFieldType(ObjectComparison.Field f) {
                expectedPaths.remove(f.getPath());
                return super.getComparisonFieldType(f);
            }
        }
        introspect(new FieldPathCheckingConfig());
        assertEquals("paths correct", 0, expectedPaths.size());
    }

    public void testIterableIntrospection() {
        t1 = new TestIterableBean(Arrays.asList("item1", "item2", "item3", "item4"));
        t2 = new TestIterableBean(Arrays.asList("item1", "item3", "item4"));
        introspect();

        checkDifferences(
            newFieldDifference(
                "iterableField.1",
                "object1:[item2] object2:[Undefined]",
                "item2",
                ObjectComparison.Field.UNDEFINED_FIELD_VALUE
            )
        );

        t1 = new TestIterableBean(Arrays.asList("item1", "item2"));
        t2 = new TestIterableBean(Arrays.asList("item2", null, "wibble"));
        introspect();

        checkDifferences(
            newFieldDifference(
                "iterableField.0",
                "object1:[item1] object2:[Undefined]",
                "item1",
                ObjectComparison.Field.UNDEFINED_FIELD_VALUE
            ),
            newFieldDifference(
                "iterableField.2",
                "object1:[Undefined] object2:[null]",
                ObjectComparison.Field.UNDEFINED_FIELD_VALUE,
                null
            ),
            newFieldDifference(
                "iterableField.3",
                "object1:[Undefined] object2:[wibble]",
                ObjectComparison.Field.UNDEFINED_FIELD_VALUE,
                "wibble"
            )
        );
    }

    public void testIterableIntrospectionWithoutIntelligentMatching() {
        t1 = new TestIterableBean(Arrays.asList("item1", "item2", "test", "wibble"));
        t2 = new TestIterableBean(Arrays.asList("item1", null, "wibble"));
        introspect(new ObjectComparison.DefaultConfig() {{
                introspectPaths(
                    new ObjectComparison.IterableIntrospector() {{
                        setUseIntelligentMatching(false);
                    }
                }, "iterableField");
            }
        });
        checkDifferences(
            newValueDifference(
                "iterableField.1",
                "object1:[item2] object2:[null]",
                "item2",
                null
            ),
            newValueDifference(
                "iterableField.2",
                "object1:[test] object2:[wibble]",
                "test",
                "wibble"
            ),
            newFieldDifference(
                "iterableField.3",
                "object1:[wibble] object2:[Undefined]",
                "wibble",
                ObjectComparison.Field.UNDEFINED_FIELD_VALUE
            )
        );
    }

    public void testUnsortedSetIntrospection() {
        Set<String> s1 = new HashSet<String>();
        s1.add("test1");
        s1.add("test4");
        s1.add("test3");
        s1.add("test2");

        Set<String> s2 = new HashSet<String>();
        s2.add("test1");
        s2.add("test5");
        s2.add("test3");
        s2.add("test2");

        t1 = s1; t2 = s2;
        introspect();
        checkDifferences(
            newFieldDifference(
                "3",
                "object1:[test4] object2:[Undefined]",
                "test4",
                ObjectComparison.Field.UNDEFINED_FIELD_VALUE
            ),
            newFieldDifference(
                "4",
                "object1:[Undefined] object2:[test5]",
                ObjectComparison.Field.UNDEFINED_FIELD_VALUE,
                "test5"
            )
        );
    }

    public void testSortedSetIntrospection() {
        Set<String> s1 = new TreeSet<String>();
        s1.add("test1");
        s1.add("test4");
        s1.add("test3");
        s1.add("test2");

        Set<String> s2 = new TreeSet<String>();
        s2.add("test1");
        s2.add("test5");
        s2.add("test3");
        s2.add("test2");

        t1 = s1; t2 = s2;
        introspect();
        checkDifferences(
            newFieldDifference(
                "3",
                "object1:[test4] object2:[Undefined]",
                "test4",
                ObjectComparison.Field.UNDEFINED_FIELD_VALUE
            ),
            newFieldDifference(
                "4",
                "object1:[Undefined] object2:[test5]",
                ObjectComparison.Field.UNDEFINED_FIELD_VALUE,
                "test5"
            )
        );
    }

    public void testArrayIntrospection() {
        t1 = new String[] { "test1", "test2", "test3" };
        t2 = new String[] { "test1", "test3" };

        introspect();
        checkDifferences(
            newFieldDifference(
                "1",
                "object1:[test2] object2:[Undefined]",
                "test2",
                ObjectComparison.Field.UNDEFINED_FIELD_VALUE
            )

        );
    }

    //there is an ArrayAsListComparator defined by default to allow us to compare arrays more sensibly
    //this is required since new int[] { 1, 2, 3 }.equals( new int[] { 1, 2, 3 } ) is false, which creates misleading comparison results
    //n.b. it is also possible to introspect arrays to get an exact list of the differences
    public void testArrayComparison() {
        TestBeanSubclass1 t1 = new TestBeanSubclass1(10d, "test", 1, new float[] { 1, 2, 3 });
        TestBeanSubclass1 t2 = new TestBeanSubclass1(10d, "test", 1, new float[] { 1, 2, 3 });
        this.t1 = t1;
        this.t2 = t2;

        introspect(defaultConfig);
        checkDifferences();

        t2.floatField = new float[] { 1, 2 };
        introspect(defaultConfig);
        assertEquals(1, differences.size());
        assertEquals(3.0f, differences.get(0).getFieldValue1());
    }

    public void testGraphCycle() {
        TestFieldDifferenceBean t1 = new TestFieldDifferenceBean(9d, "test");
        TestFieldDifferenceBean t2 = new TestFieldDifferenceBean(9d, "test");
        this.t2 = t2;
        this.t1 = t1;

        t1.beanField = t1;
        introspect(beanInstrospectingConfig);
        //t1 has a reference back to itself, so differs from t2
        checkDifferences(
            newValueDifference(
                "beanField",
                "object1:[TestFieldDifferenceBean{doubleField=9.0, stringField='test', colorField=null}] object2:[null]",
                t1,
                null
            )
        );

        //both beans have references/cycles in the reference graph
        //so they are both the same, but we need to check this to make sure the maxDepth prevents infinite recursion
        t2.beanField = t2;
        introspect(beanInstrospectingConfig);
        checkDifferences(
        );
    }

    public void testGraphCycleDifferences() {
        TestFieldDifferenceBean test1 = new TestFieldDifferenceBean(10d, "test");
        TestFieldDifferenceBean test2 = new TestFieldDifferenceBean(10d, "test");
        t1 = new TestFieldDifferenceBean(10d, "test", test1);
        t2 = new TestFieldDifferenceBean(10d, "test", test2);
        test1.beanField = (TestFieldDifferenceBean)t1;
        test2.beanField = test2;

        introspect(beanInstrospectingConfig);
        //cycle back to the root for t1, back to beanField for t2
        checkDifferences(
            newCycleDifference(
                ObjectComparison.INPUT_OBJECT_TEXT,
                "object1:[] object2:[beanField]",
                t1,
                test2,
                "beanField", "beanField"
            )
        );
    }

    public void testIgnorePatterns() {
        t1 = new TestFieldDifferenceBean(10d, "test", Color.RED);
        t2 = new TestFieldDifferenceBean(10d, "test", Color.BLACK);

        ObjectComparison c = new ObjectComparison().ignorePaths("color.*");
        differences = c.getDifferences(t1, t2);
        checkDifferences();

        t1 = new TestFieldDifferenceBean(0d, "test", new TestFieldDifferenceBean(2d, "test"));
        t2 = new TestFieldDifferenceBean(1d, "test", new TestFieldDifferenceBean(3d, "test"));

        c.introspectPaths("beanField");
        differences = c.getDifferences(t1, t2);
        checkDifferences(
            newValueDifference(
                "doubleField",
                "object1:[0.0] object2:[1.0]",
                0d,
                1d
            ),
            newValueDifference(
                "doubleField",
                "object1:[2.0] object2:[3.0]",
                2d,
                3d,
                "beanField"
            )
        );

        c.ignorePaths(".*doubleField");
        differences = c.getDifferences(t1, t2);
        checkDifferences();
    }

    private void introspect() {
        introspect(new ObjectComparison.DefaultConfig());
    }

    private void introspect(ObjectComparison.Config f) {
        differences = new ObjectComparison(f).getDifferences(t1, t2);
    }

    private void introspect(String object1Description, String object2Description, ObjectComparison.Config f) {
        ObjectComparison c = new ObjectComparison(f);
        c.setDescription1(object1Description);
        c.setDescription2(object2Description);
        differences = c.getDifferences(t1, t2);
    }

    private void checkDifferences(ObjectComparison.Difference... diffs) {
        List<ObjectComparison.Difference> expectedDifferences = Arrays.asList(diffs);
        int maxDiffs = Math.max(expectedDifferences.size(), differences.size());
        StringBuilder failText = new StringBuilder();
        for ( int loop=0; loop < maxDiffs; loop++) {
            ObjectComparison.Difference expected = getDifferenceAt(expectedDifferences, loop);
            ObjectComparison.Difference actual = getDifferenceAt(differences, loop);
            if ( (expected == null || actual == null) ||  ! expected.equals(actual) ) {
                failText.append("Expected at position ").append(loop).append(": \n ").append(expected).append("\n, actual: \n ").append(actual).append("\n");
            }
        }
        if ( failText.length() > 0) {
            fail(failText.toString());
        }
    }

    private ObjectComparison.Difference getDifferenceAt(List<ObjectComparison.Difference> diffs, int loop) {
        ObjectComparison.Difference expected = loop >= diffs.size() ? null : diffs.get(loop);
        return expected;
    }


    public static class TestFieldDifferenceBean {

        private double doubleField;
        private String stringField;
        private Color colorField;
        protected TestFieldDifferenceBean beanField;

        public TestFieldDifferenceBean(double doubleField, String stringField) {
            this.doubleField = doubleField;
            this.stringField = stringField;
        }

        public TestFieldDifferenceBean(double doubleField, String stringField, Color colorField) {
            this.doubleField = doubleField;
            this.stringField = stringField;
            this.colorField = colorField;
        }

        public TestFieldDifferenceBean(double doubleField, String stringField, TestFieldDifferenceBean beanField) {
            this.doubleField = doubleField;
            this.stringField = stringField;
            this.beanField = beanField;
        }

        @Override
        public String toString() {
            return "TestFieldDifferenceBean{" +
                    "doubleField=" + doubleField +
                    ", stringField='" + stringField + '\'' +
                    ", colorField=" + colorField +
                    '}';
        }
    }

    public static class TestMapBean {
        private Map mapField;

        public TestMapBean(Map mapField) {
            this.mapField = mapField;
        }

    }

    public static class TestIterableBean {
        private List<String> iterableField = new ArrayList<String>();

        public TestIterableBean(List<String> iterableField) {
            this.iterableField = iterableField;
        }
    }

    public static class TestBeanSubclass1 extends TestFieldDifferenceBean {

        private int intField;
        private float[] floatField;

        public TestBeanSubclass1(double doubleField, String stringField, int intField, float[] floatField) {
            super(doubleField, stringField);
            this.intField = intField;
            this.floatField = floatField;
        }
    }

    public static class TestBeanSubclass2 extends TestFieldDifferenceBean {

        private int intField;
        private List<TestFieldDifferenceBean> listField;

        public TestBeanSubclass2(double doubleField, String stringField, int intField, List<TestFieldDifferenceBean> listField) {
            super(doubleField, stringField);
            this.intField = intField;
            this.listField = listField;
        }
    }

    private ObjectComparison.Difference newCycleDifference(String fieldName, String description, Object fieldValue1, Object fieldValue2, String... path) {
        return new ObjectComparison.Difference(ObjectComparison.DifferenceType.CYCLE, Arrays.asList(path), fieldName, description, fieldValue1, fieldValue2);
    }

    private ObjectComparison.Difference newValueDifference(String fieldName, String description, Object fieldValue1, Object fieldValue2) {
        return new ObjectComparison.Difference(ObjectComparison.DifferenceType.VALUE, fieldName, description, fieldValue1, fieldValue2);
    }

    private ObjectComparison.Difference newValueDifference(String fieldName, String description, Object fieldValue1, Object fieldValue2, String... path ) {
        return new ObjectComparison.Difference(ObjectComparison.DifferenceType.VALUE, Arrays.asList(path), fieldName, description, fieldValue1, fieldValue2);
    }

    private ObjectComparison.Difference newFieldDifference(String fieldName, String description, Object fieldValue1, Object fieldValue2, String... path ) {
        return new ObjectComparison.Difference(ObjectComparison.DifferenceType.FIELD, Arrays.asList(path), fieldName, description, fieldValue1, fieldValue2);
    }

    private ObjectComparison.Difference newClassDifference(String fieldName, String description, Object fieldValue1, Object fieldValue2, String... path ) {
        return new ObjectComparison.Difference(ObjectComparison.DifferenceType.CLASS, Arrays.asList(path), fieldName, description, fieldValue1, fieldValue2);
    }

    private static class BeanFieldIntrospectingConfig extends ObjectComparison.DefaultConfig {

        public ObjectComparison.ComparisonFieldType getComparisonFieldType(ObjectComparison.Field f) {
            ObjectComparison.ComparisonFieldType result = super.getComparisonFieldType(f);
            if ( f.getType() == TestFieldDifferenceBean.class ) {
                result = ObjectComparison.ComparisonFieldType.INTROSPECTION_FIELD;
            }
            return result;
        }
    }

    private static class SuperclassIntrospectingConfig extends BeanFieldIntrospectingConfig {

        public ObjectComparison.FieldIntrospector getFieldIntrospector(String fieldPath, Class commonSuperclass, Object o1, Object o2) {
            ObjectComparison.FieldIntrospector result = super.getFieldIntrospector(fieldPath, commonSuperclass, o1, o2);
            if ( TestFieldDifferenceBean.class.isAssignableFrom(commonSuperclass) ) {
                result = new ObjectComparison.SuperclassFieldIntrospector();
            }
            return result;
        }
    }

    //config with an introspector which includes subclass fields
    private static class SubclassIntrospectingConfig extends BeanFieldIntrospectingConfig {

        public ObjectComparison.FieldIntrospector getFieldIntrospector(String fieldPath, Class commonSuperclass, Object o1, Object o2) {
            ObjectComparison.FieldIntrospector result = super.getFieldIntrospector(fieldPath, commonSuperclass, o1, o2);
            if ( TestFieldDifferenceBean.class.isAssignableFrom(commonSuperclass) ) {
                result = new ObjectComparison.SubclassFieldIntrospector();
            }
            return result;
        }
    }
}
