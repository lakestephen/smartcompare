import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Nick Ebbutt
 * Date: 14-Jul-2009
 * Time: 13:38:06
 *
 * A class which compares field values of two Objects to produce a list of differences.
 * It can also introspect several levels down the bean graph (until it reaches the level defined in maxDepth).
 *
 * The default config compares fields with primative types or which implement CharSequence (e.g. Strings)
 * at the top level only (no drill down into fields)
 *
 * To configure the calculator to introspect further down the bean graph you have to pass in a Config object,
 * in which you can define:
 *
 * --> which fields to compare, and optionally a comparator to compare them
 *     Any field which is returned as a comparison field is considered a 'leaf' for the comparison, and drill down
 *     will not occur.
 *
 * --> which fields to 'introspect'
 *     These are drill down fields for the comparison, and the difference calculator will walk down the bean path
 *     for these fields.
 *
 * Any fields which are not 'introspection' fields or 'comparison' fields are simply ignored.
 *
 * It's also possible to configure how introspection is performed -->
 *
 * The Config is asked to supply a FieldIntrospector instance when introspection starts.
 * The FieldIntrospector's responsibility is to parse the object(s) being compared to determine the list of Fields they support
 * Comparison/Introspection can then take place on each field returned.
 *
 * A 'Field' here does not necessarily imply a field on a class at a language level, although this is the default -
 * FieldDifferenceCalculator has it's own Field abstraction which makes it possible for FieldIntrospectors to work at a higher level
 * For example, it is possible for an Introspector to return a list of the values in a Map as Fields, where the fields are
 * identified by the keys in the Map. This allows introspection/comparison of Map instances as part of the graph.
 *
 * TODO - check synthetic fields (currently excluded)
 * TODO - exclude static fields
 */
public class FieldDifferenceCalculator {

    public static final String INPUT_OBJECT_TEXT = "input object for comparison";

    private static Config DEFAULT_FIELD_ANALYZER = new DefaultConfig();
    private static final int DEFAULT_MAX_DEPTH = 3;

    private List<String> path;
    private volatile String description1;
    private volatile String description2;
    private volatile int maxDepth;
    private Config fieldAnalyzer;

    public FieldDifferenceCalculator() {
        this(DEFAULT_FIELD_ANALYZER);
    }

    public FieldDifferenceCalculator(Config fieldAnalyzer) {
        this("object1", "object2", fieldAnalyzer);
    }

    public FieldDifferenceCalculator(String description1, String description2) {
        this(description1, description2, DEFAULT_FIELD_ANALYZER, Collections.EMPTY_LIST, DEFAULT_MAX_DEPTH);
    }

    public FieldDifferenceCalculator(String description1, String description2, Config fieldAnalyzer) {
        this(description1, description2, fieldAnalyzer, Collections.EMPTY_LIST, DEFAULT_MAX_DEPTH);
    }

    private FieldDifferenceCalculator(String description1, String description2, Config fieldAnalyzer, List<String> path, int maxDepth) {
        this.fieldAnalyzer = fieldAnalyzer;
        this.path = path;
        this.description1 = description1;
        this.description2 = description2;
        this.maxDepth = maxDepth;
    }

    public void setDescription1(String description1) {
        this.description1 = description1;
    }

    public void setDescription2(String description2) {
        this.description2 = description2;
    }

    /**
     * Set how far down the bean graph to introspect in the attempt to find differences
     * It is important to set a sensible maximum here since otherwise any cycles in the reference graph may cause a stack overflow
     */
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public synchronized List<Difference> getDifferences(Object o1, Object o2) {

        List<Difference> result = new ArrayList<Difference>();
        if ( o1 != null || o2 != null) {
            Difference d = getNullDifference(INPUT_OBJECT_TEXT, o1, o2);
            if ( d != null) {
                result.add(d);
            } else {
                d = getClassDifference(o1, o2);
                if ( d != null) {
                    result.add(d);
                } 
                result.addAll(getFieldDifferences(o1, o2));
            }
        }
        return result;
    }

    private Difference getClassDifference(Object o1, Object o2) {
        Difference result = null;
        //if these are the original input object rather than a bean path further down we use a special description
        String fieldName = path.size() == 0 ? INPUT_OBJECT_TEXT : "";
        if (! o1.getClass().equals(o2.getClass())) {
            result = new Difference(path, fieldName, "different class type: object1: [" + o1.getClass().getName() + "] object2: [" + o2.getClass().getName() + "]", o1.getClass(), o2.getClass());
        }
        return result;
    }

    private List<Difference> getFieldDifferences(Object o1, Object o2) {

        List<Field> introspectionFields = new ArrayList<Field>();
        List<Field> comparisonFields = new ArrayList<Field>();

        Class clazz = ClassUtils.getCommonSuperclass(o1.getClass(), o2.getClass());
        addFields(clazz, o1, o2, introspectionFields, comparisonFields);

        List<Difference> result = new ArrayList<Difference>();
        result.addAll(getFieldDifferences(comparisonFields, new ComparisonFieldDiffCalculator(), o1, o2));
        result.addAll(getFieldDifferences(introspectionFields, new IntrospectionFieldDifferenceCalculator(), o1, o2));
        return result;
    }

    private void addFields(Class clazz, Object o1, Object o2, List<Field> introspectionFields, List<Field> comparisonFields) {

        FieldIntrospector i = fieldAnalyzer.getFieldIntrospector(clazz, o1, o2);
        List<Field> fields = i.getFields();
        for ( Field f : fields) {
            switch ( fieldAnalyzer.getComparisonFieldType(f)) {
                case INTROSPECTION_FIELD :
                    introspectionFields.add(f);
                    break;
                case COMPARISON_FIELD:
                    comparisonFields.add(f);
                    break;
            }
        }
    }

    private List<Difference> getFieldDifferences(List<Field> comparisonField, FieldDiffCalculator fieldDiffCalculator, Object o1, Object o2) {
        List<Difference> result = new ArrayList<Difference>();
        for ( Field f : comparisonField) {
            try {
                Object fieldValue1 = f.getValue(o1);
                Object fieldValue2 = f.getValue(o2);

                if ( fieldValue1 != fieldValue2) {
                    //check to see if one of the fields is null
                    Difference d = getNullDifference(f.getName(), fieldValue1, fieldValue2);
                    if ( d != null ) {
                        result.add(d);
                    } else {
                        result.addAll(fieldDiffCalculator.getFieldDifferences(
                            f, fieldValue1, fieldValue2)
                        );
                    }
                }
            } catch (Exception e) {
                result.add(new Difference(path, f.getName(), e.getClass().getName() + " - cannot determine equality", "Exception during comparison", "Exception during comparison"));
            }
        }
        return result;
    }

    //An introspection field, in which case we test for equality by reference, and if that
    //test fails we drill down to look for differences one further step down the object graph
    private class IntrospectionFieldDifferenceCalculator implements FieldDiffCalculator {

        public List<Difference> getFieldDifferences(Field f, Object fieldValue1, Object fieldValue2) {
            List<String> newPath = new ArrayList<String>(path);
            newPath.add(f.getName());
            List<Difference> result = new ArrayList<Difference>();
            if ( newPath.size() <= maxDepth ) {
                FieldDifferenceCalculator l = new FieldDifferenceCalculator(
                        description1,
                        description2,
                        fieldAnalyzer,
                        newPath,
                        maxDepth
                );
                result.addAll(l.getDifferences(fieldValue1, fieldValue2));
            }
            return result;
        }
    }

    //A comparison field, in which case we test for equality to determine differences
    //using a supplied Comparator, compareTo for Comparables, or equals()
    private class ComparisonFieldDiffCalculator implements FieldDiffCalculator {

        public List<Difference> getFieldDifferences(Field f, Object fieldValue1, Object fieldValue2) {
            Difference fieldDifference = getFieldDifference(f, fieldValue1, fieldValue2);

            List<Difference> result = new ArrayList<Difference>();
            if ( fieldDifference != null) {
                result.add(fieldDifference);
            }
            return result;
        }

        private Difference getFieldDifference(Field f, Object fieldValue1, Object fieldValue2) {
            Difference fieldDifference;
            Comparator c = fieldAnalyzer.getComparator(f);
            if ( c != null ) {
                int comparison = c.compare(fieldValue1, fieldValue2);
                fieldDifference = createComparisonDifference(f.getName(), fieldValue1, fieldValue2, comparison == 0);
            } else if ( fieldValue1 instanceof Comparable ) {
                int comparison = ((Comparable)fieldValue1).compareTo(fieldValue2);
                fieldDifference = createComparisonDifference(f.getName(), fieldValue1, fieldValue2, comparison == 0);
            } else {
                fieldDifference = createComparisonDifference(f.getName(), fieldValue1, fieldValue2, fieldValue1.equals(fieldValue2));
            }
            return fieldDifference;
        }
    }

    private interface FieldDiffCalculator {
        List<Difference> getFieldDifferences(Field f, Object fieldValue1, Object fieldValue2);
    }

    private Difference createComparisonDifference(String fieldName, Object fieldValue1, Object fieldValue2, boolean comparisonSucceeded) {
        Difference d = null;
        if (! comparisonSucceeded) {
            d = new Difference(path, fieldName, description1 + ":[" + fieldValue1 + "] " + description2 + ":[" + fieldValue2 + "]", fieldValue1, fieldValue2);
        }
        return d;
    }

    private Difference getNullDifference(String fieldName, Object fieldValue1, Object fieldValue2) {
        Difference d = null;
        boolean isNull1 = fieldValue1 == null;
        boolean isNull2 = fieldValue2 == null;
        if ( isNull1 != isNull2 ) {
            d = createComparisonDifference(fieldName, fieldValue1, fieldValue2, false);
        }
        return d;
    }

    public boolean isSupportedForComparison(Field f) {
        return false;
    }


    /**
     * General util methods for parsing class information
     */
    private static class ClassUtils {

        private static Class getCommonSuperclass(Class c1, Class c2) {
            return getCommonSuperclass(c1, new Stack<Class>(), c2, new Stack<Class>());
        }

        private static Class getCommonSuperclass(Class c1, Stack<Class> classStack1, Class c2, Stack<Class> classStack2) {
            Class result;
            if ( c1 == c2 ) {
                result = c1;
            } else if ( c1 == null) {
                result = c2;
            } else if (c2 == null) {
                result = c1;
            } else {

                //the classes of the values in the map with this key differ
                //find the shared superclass
                classStack1.clear();
                classStack2.clear();
                addToStack(classStack1, c1);
                addToStack(classStack2, c2);
                result = findFirstMatching(classStack1, classStack2);
            }
            return result;
        }

        //we have 2 stacks of classes with Object.class at the top and then more specific classes
        //this should find the first point in the stack where the classes differ, then return the previous
        //classtype (which should be the most specific common superclass)
        private static Class<?> findFirstMatching(Stack<Class> classStack1, Stack<Class> classStack2) {
            Class result = Object.class;
            while(classStack1.size() > 0 && classStack2.size() > 0) {
                Class c1 = classStack1.pop();
                Class c2 = classStack2.pop();
                if ( c1 != c2 ) {
                    break;
                }
                result = c1;
            }
            return result;
        }

        //recursively add to stack until we reach Object
        private static void addToStack(Stack<Class> classStack, Class c) {
            classStack.add(c);
            Class superclass = c.getSuperclass();
            if ( superclass != null) {
                addToStack(classStack, superclass);
            }
        }
    }

    public static class Difference {

        private List<String> path;
        private String fieldName;
        private String description;
        private Object fieldValue1;
        private Object fieldValue2;
        private String fullFieldPath;

        public Difference(String fieldName, String description, Object fieldValue1, Object fieldValue2) {
            this(Collections.EMPTY_LIST, fieldName, description, fieldValue1, fieldValue2);
        }

        public Difference(List<String> path, String fieldName, String description, Object fieldValue1, Object fieldValue2) {
            this.path = path;
            this.fieldName = fieldName;
            this.description = description;
            this.fieldValue1 = fieldValue1;
            this.fieldValue2 = fieldValue2;
            calculateFieldPath();
        }

        public Object getFieldValue1() {
            return fieldValue1;
        }

        public Object getFieldValue2() {
            return fieldValue2;
        }

        public String getFieldPath() {
            return fullFieldPath;
        }

        private void calculateFieldPath() {
            StringBuilder b = new StringBuilder();
            appendPath(b);
            appendfieldName(b);
            this.fullFieldPath = b.toString();
        }

        private void appendfieldName(StringBuilder b) {
            if ( fieldName != null && fieldName.length() > 0) {
                if ( path.size() > 0 ) {
                    b.append(".");
                }
                b.append(fieldName);
            }
        }

        private void appendPath(StringBuilder b) {
            Iterator<String> i = path.iterator();
            String p;
            while(i.hasNext()) {
                b.append(i.next());
                if ( i.hasNext()) {
                    b.append(".");
                }
            }
        }

        public String toString() {
            StringBuilder b = new StringBuilder(fullFieldPath);
            b.append("->").append(description);
            return b.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Difference that = (Difference) o;

            if (description != null ? !description.equals(that.description) : that.description != null) return false;
            if (fieldValue1 != null ? !fieldValue1.equals(that.fieldValue1) : that.fieldValue1 != null) return false;
            if (fieldValue2 != null ? !fieldValue2.equals(that.fieldValue2) : that.fieldValue2 != null) return false;
            if (fullFieldPath != null ? !fullFieldPath.equals(that.fullFieldPath) : that.fullFieldPath != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = path != null ? path.hashCode() : 0;
            result = 31 * result + (fieldName != null ? fieldName.hashCode() : 0);
            result = 31 * result + (description != null ? description.hashCode() : 0);
            result = 31 * result + (fieldValue1 != null ? fieldValue1.hashCode() : 0);
            result = 31 * result + (fieldValue2 != null ? fieldValue2.hashCode() : 0);
            result = 31 * result + (fullFieldPath != null ? fullFieldPath.hashCode() : 0);
            return result;
        }
    }

    public static class DefaultConfig implements Config {

        public ComparisonFieldType getComparisonFieldType(Field f) {
            //only introspect top level fields by default, no drill down
            return f.getType().isPrimitive() || CharSequence.class.isAssignableFrom(f.getType()) ?
                    ComparisonFieldType.COMPARISON_FIELD : ComparisonFieldType.IGNORE_FIELD;
        }

        public Comparator getComparator(Field f) {
            return null;
        }

        public FieldIntrospector getFieldIntrospector(Class commonSuperclass, Object o1, Object o2) {
            if ( Map.class.isAssignableFrom(commonSuperclass) ) {
                return new MapIntrospector(o1, o2);
            } else {
                return new IdenticalClassFieldIntrospector(commonSuperclass, o1, o2);
            }
        }
    }

    /**
     * This class introspects fields for two Objects.
     * The objects may or may not share a common superclass.
     * Fields with the same name but from different classes are logically different, and are treated as distinct 
     */
    public static class SubclassFieldIntrospector extends ReflectionFieldIntrospector {
        private Class commonSuperclass;
        private Class o1;
        private Class o2;

        public SubclassFieldIntrospector(Class commonSuperclass, Object o1, Object o2) {
            this.commonSuperclass = commonSuperclass;
            this.o1 = o1.getClass();
            this.o2 = o2.getClass();
        }

        public List<Field> getFields() {
            List<Field> result = new ArrayList<Field>();
            //add fields from shared superclass
            addFieldsRecursivelyUntilReachingClass(result, commonSuperclass, Object.class);
            addFieldsRecursivelyUntilReachingClass(result, o1, commonSuperclass); //fields only in o1 hierarchy
            addFieldsRecursivelyUntilReachingClass(result, o2, commonSuperclass); //fields only in o2 hierarchy
            return result;
        }
    }

    /**
     * This class introspects fields from the common superclass upwards for classes which share a common superclass
     * which is not Object.class. Fields at a subclass level are not included
     */
    public static class SuperclassFieldIntrospector extends ReflectionFieldIntrospector {
        private Class commonSuperclass;

        public SuperclassFieldIntrospector(Class commonSuperclass, Object o1, Object o2) {
            this.commonSuperclass = commonSuperclass;
        }

        public List<Field> getFields() {
            List<Field> result = new ArrayList<Field>();
            //add fields from shared superclass
            addFieldsRecursivelyUntilReachingClass(result, commonSuperclass, Object.class);
            return result;
        }
    }

    /*
    * This introspector uses reflection to find fields for two objects which are of identical class
    * If the class type differs, no fields will be returned
    */
    public static class IdenticalClassFieldIntrospector extends ReflectionFieldIntrospector {

        private Class commonSuperclass;
        private Class o1;
        private Class o2;

        public IdenticalClassFieldIntrospector(Class commonSuperclass, Object o1, Object o2) {
            this.commonSuperclass = commonSuperclass;
            this.o1 = o1.getClass();
            this.o2 = o2.getClass();
        }

        public List<Field> getFields() {
            List<Field> result = new ArrayList<Field>();
            //only introspect fields if the two objects are exactly the same class type, no subclasses
            if ( o1 == o2 && o1 == commonSuperclass ) {
                addFieldsRecursivelyUntilReachingClass(result, commonSuperclass, Object.class);
            }
            return result;
        }
    }

     public static abstract class ReflectionFieldIntrospector implements FieldIntrospector {

        //find all the fields for this class and superclasses
        protected void addFieldsRecursivelyUntilReachingClass(List<Field> result, Class c, Class endClass) {
            if ( c != endClass ) {
                java.lang.reflect.Field[] fields = c.getDeclaredFields();
                for ( final java.lang.reflect.Field f : fields) {
                    if ( ! f.isSynthetic() ) {
                        result.add(new Field() {
                            public Class<?> getType() {
                                return f.getType();
                            }

                            public Object getValue(Object o1) throws IllegalAccessException {
                                if ( ! f.isAccessible()) {
                                    f.setAccessible(true);
                                }
                                return f.getDeclaringClass().isAssignableFrom(o1.getClass()) ? f.get(o1) : null;
                            }

                            public String getName() {
                                return f.getName();
                            }
                        });
                    }
                }

                Class superclass = c.getSuperclass();
                if ( superclass != null) {
                    addFieldsRecursivelyUntilReachingClass(result, superclass, endClass);
                }
            }
        }
    }

    /**
     * Map introspector
     */
    public static class MapIntrospector implements FieldIntrospector {

        private Map o1;
        private Map o2;
        private Stack<Class> classStack1 = new Stack<Class>();
        private Stack<Class> classStack2 = new Stack<Class>();

        public MapIntrospector(Object o1, Object o2) {
            this.o1 = (Map)o1;
            this.o2 = (Map)o2;
        }

        public List<Field> getFields() {
            Set<Object> allKeys = new HashSet<Object>();
            allKeys.addAll(o1.keySet());
            allKeys.addAll(o2.keySet());

            List<Field> fields = new ArrayList<Field>();
            for ( final Object key : allKeys) {
                fields.add(new Field() {

                    public Class<?> getType() {
                        return getCommonSuperclass(key);
                    }

                    private Class<?> getCommonSuperclass(Object key) {
                        Class result;
                        //this is a bit tricky because the values in the maps may have different class types
                        //in which case we need to find and return the most specific common superclass.
                        Class c1 = getClass(key, o1);
                        Class c2 = getClass(key, o2);
                        result = ClassUtils.getCommonSuperclass(c1, classStack1, c2, classStack2);
                        return result;
                    }

                    private Class getClass(Object key, Map o1) {
                        Object o = o1.get(key);
                        return o == null ? null : o.getClass();
                    }

                    public Object getValue(Object o) throws Exception {
                        if ( o == o1) {
                            return o1.get(key);
                        } else {
                            return o2.get(key);
                        }
                    }

                    public String getName() {
                        return key.toString();
                    }
                });
            }
            return fields;
        }
    }

    /**
     * Defines how the calculator processes the fields
     */
    public static interface Config {

        /**
         * @return a type which indicates whether this field should be compared,
         * whether we should introspect it to drill down further, or ignore it
         */
        ComparisonFieldType getComparisonFieldType(Field f);

        /**
         * @return comparator to use for Field in cases where the fields compared are not equal by reference.
         *
         * If this method returns null, the comparison objects will be considered equal if -->
         * 1- the object class implements Comparable and compareTo returns zero, or
         * 2- equals() returns true
         */
        Comparator getComparator(Field f);


        /**
         * @return an introspector which is responsible for extracting field details for the given comparison objects
         * which are either instances of Class c, or of subclasses
         */
        FieldIntrospector getFieldIntrospector(Class c, Object o1, Object o2);

    }

    /**
     * FieldIntrospector calculates the fields for an object which is being introspected
     *
     * The default implementation uses reflection to create a List of Field which represent the java.lang.reflect.Field
     * in the class hierarchy
     *
     * Other implementations may, for example, introspect on Map classes and represent the keys/values
     * in the map as field instances
     */
    public static interface FieldIntrospector {

        List<Field> getFields();
    }

    /**
     * An abstract representation of a Field discovered by introspecting a class/object instance
     */
    public static interface Field {

        /**
         * @return class type for this field (values returned may be subclasses)
         */
        Class<?> getType();

        /**
         * @return value for this field, which should be the class given by getType() or a subclass, or null
         */
        Object getValue(Object o) throws Exception;

        /**
         * The field names should perferably be unique per class, but duplicate names are tolerated
         * @return Name for this field, used to describe the differences.
         */
        String getName();
    }

    public enum ComparisonFieldType {
        COMPARISON_FIELD,
        INTROSPECTION_FIELD,
        IGNORE_FIELD
    }

}
