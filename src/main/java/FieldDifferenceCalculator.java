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

    public static final String INPUT_OBJECT_TEXT = "";

    private static Config DEFAULT_FIELD_ANALYZER = new DefaultConfig();

    private List<String> path;
    private volatile String description1;
    private volatile String description2;
    private List<Object> visitedNodes1;
    private List<Object> visitedNodes2;
    private Config fieldAnalyzer;

    public FieldDifferenceCalculator() {
        this(DEFAULT_FIELD_ANALYZER);
    }

    public FieldDifferenceCalculator(Config fieldAnalyzer) {
        this("object1", "object2", fieldAnalyzer);
    }

    public FieldDifferenceCalculator(String description1, String description2) {
        this(description1, description2, DEFAULT_FIELD_ANALYZER, Collections.EMPTY_LIST, new ArrayList<Object>(), new ArrayList<Object>());
    }

    public FieldDifferenceCalculator(String description1, String description2, Config fieldAnalyzer) {
        this(description1, description2, fieldAnalyzer, Collections.EMPTY_LIST, new ArrayList<Object>(), new ArrayList<Object>());
    }

    private FieldDifferenceCalculator(String description1, String description2, Config fieldAnalyzer, List<String> path, List<Object> visitedNodes1, List<Object> visitedNodes2) {
        this.fieldAnalyzer = fieldAnalyzer;
        this.path = path;
        this.description1 = description1;
        this.description2 = description2;
        this.visitedNodes1 = visitedNodes1;
        this.visitedNodes2 = visitedNodes2;
    }

    public void setDescription1(String description1) {
        this.description1 = description1;
    }

    public void setDescription2(String description2) {
        this.description2 = description2;
    }

    public synchronized List<Difference> getDifferences(Object o1, Object o2) {
        List<Difference> result = new ArrayList<Difference>();
        if ( o1 != null || o2 != null) {
            boolean nullDifference = addNullDifference(result, INPUT_OBJECT_TEXT, o1, o2);
            if ( ! nullDifference ) {
                boolean cycleExists = addCycleDifference(result, o1, o2);
                if ( ! cycleExists ) {
                    addClassDifference(result, o1, o2);
                    addFieldDifferences(result, o1, o2);
                }
            }
        }
        return result;
    }

    private boolean addCycleDifference(List<Difference> differences, Object o1, Object o2) {
        int cycle1PathIndex = getIndexByReferenceEquality(visitedNodes1, o1);
        int cycle2PathIndex = getIndexByReferenceEquality(visitedNodes2, o2);
        boolean cycleExists = false;
        if ( cycle1PathIndex != -1 || cycle2PathIndex != -1 ) {
            cycleExists = true;
            //if the cycle points to the same index in the path for both objects we don't treat this as a difference.
            //we just stop parsing so that we don't end up in an infinite loop
            if ( cycle1PathIndex != cycle2PathIndex ) {
                differences.add(
                    new Difference(DifferenceType.CYCLE, path,
                        INPUT_OBJECT_TEXT,
                        getDifferenceDescription(
                            ClassUtils.getPathAsString(path.subList(0, cycle1PathIndex)),
                            ClassUtils.getPathAsString(path.subList(0, cycle2PathIndex))
                        ),
                        o1,
                        o2
                    )
                );
            }
        }
        return cycleExists;
    }

    private int getIndexByReferenceEquality(List<Object> visitedNodes, Object o) {
        int result = -1;
        int index = 0;
        for ( Object node : visitedNodes ) {
            if ( node == o) {
                result = index;
                break;
            }
            index++;
        }
        return result;
    }


    private boolean addClassDifference(List<Difference> differences, Object o1, Object o2) {
        boolean result = false;
        //if these are the original input object rather than a bean path further down we use a special description
        String fieldName = path.size() == 0 ? INPUT_OBJECT_TEXT : "";
        if (! o1.getClass().equals(o2.getClass())) {
            result = differences.add(new Difference(DifferenceType.CLASS, path, fieldName, "different class type: object1: [" + o1.getClass().getName() + "] object2: [" + o2.getClass().getName() + "]", o1.getClass(), o2.getClass()));
        }
        return result;
    }

    private boolean addFieldDifferences(List<Difference> differences, Object o1, Object o2) {
        List<Field> introspectionFields = new ArrayList<Field>();
        List<Field> comparisonFields = new ArrayList<Field>();

        Class clazz = ClassUtils.getCommonSuperclass(o1.getClass(), o2.getClass());
        addFields(clazz, o1, o2, introspectionFields, comparisonFields);

        boolean result = differences.addAll(getFieldDifferences(comparisonFields, new ComparisonFieldDiffCalculator(), o1, o2));
        result |= differences.addAll(getFieldDifferences(introspectionFields, new IntrospectionFieldDifferenceCalculator(o1, o2), o1, o2));
        return result;
    }

    private void addFields(Class clazz, Object o1, Object o2, List<Field> introspectionFields, List<Field> comparisonFields) {

        FieldIntrospector i = fieldAnalyzer.getFieldIntrospector(path, clazz, o1, o2);
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

    private List<Difference> getFieldDifferences(List<Field> fields, FieldDiffCalculator fieldDiffCalculator, Object o1, Object o2) {
        List<Difference> result = new ArrayList<Difference>();

        for ( Field f : fields) {
            try {
                Object fieldValue1 = f.getValue(o1);
                Object fieldValue2 = f.getValue(o2);

                if ( fieldValue1 != fieldValue2) {
                    //check to see if one of the fields is null
                    Difference d = getUndefinedFieldDifference(f.getName(), fieldValue1, fieldValue2);
                    if ( d != null ) {
                        result.add(d);
                    } else {
                        boolean nullDifference = addNullDifference(result, f.getName(), fieldValue1, fieldValue2);
                        if ( ! nullDifference ){
                            result.addAll(fieldDiffCalculator.getFieldDifferences(
                                f, fieldValue1, fieldValue2)
                            );
                        }
                    }
                }
            } catch (Throwable t) {
                throw new FieldDiferenceCalculatorException(t);
            }
        }
        return result;
    }

    //An introspection field, in which case we test for equality by reference, and if that
    //test fails we drill down to look for differences one further step down the object graph
    private class IntrospectionFieldDifferenceCalculator implements FieldDiffCalculator {
        private List<Object> newVisitedNodes1;
        private List<Object> newVisitedNodes2;

        public IntrospectionFieldDifferenceCalculator(Object o1, Object o2) {
            newVisitedNodes1 = getNewVisitedNodes(visitedNodes1, o1);
            newVisitedNodes2 = getNewVisitedNodes(visitedNodes2, o2);
        }

        public List<Difference> getFieldDifferences(Field f, Object fieldValue1, Object fieldValue2) {
            List<String> newPath = new ArrayList<String>(path);
            newPath.add(f.getName());

            List<Difference> result = new ArrayList<Difference>();
            FieldDifferenceCalculator l = new FieldDifferenceCalculator(
                    description1,
                    description2,
                    fieldAnalyzer,
                    newPath,
                    newVisitedNodes1,
                    newVisitedNodes2
            );
            result.addAll(l.getDifferences(fieldValue1, fieldValue2));
            return result;
        }

        private List<Object> getNewVisitedNodes(List<Object> visited, Object newNode) {
            ArrayList<Object> o = new ArrayList<Object>(visited);
            o.add(newNode);
            return o;
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
            d = new Difference(DifferenceType.VALUE, path, fieldName, getDifferenceDescription(fieldValue1, fieldValue2), fieldValue1, fieldValue2);
        }
        return d;
    }

    private boolean addNullDifference(List<Difference> differences, String fieldName, Object fieldValue1, Object fieldValue2) {
        boolean isNull1 = fieldValue1 == null;
        boolean isNull2 = fieldValue2 == null;
        boolean result = false;
        if ( isNull1 != isNull2 ) {
            result = differences.add(createComparisonDifference(fieldName, fieldValue1, fieldValue2, false));
        }
        return result;
    }

    //one of the two comparison objects does not define a field which the other defines
    //e.g. these are two different subclasses with differing fields at a subclass level
    private Difference getUndefinedFieldDifference(String fieldName, Object fieldValue1, Object fieldValue2) {
        Difference d = null;
        if (fieldValue1 == FieldIntrospector.UNDEFINED_FIELD || fieldValue2 == FieldIntrospector.UNDEFINED_FIELD) {
            d = new Difference(DifferenceType.FIELD, path, fieldName, getDifferenceDescription(fieldValue1, fieldValue2), fieldValue1, fieldValue2);
        }
        return d;
    }

    private String getDifferenceDescription(Object differenceValue1, Object differenceValue2) {
        return description1 + ":[" + differenceValue1 + "] " + description2 + ":[" + differenceValue2 + "]";
    }

    public boolean isSupportedForComparison(Field f) {
        return false;
    }


    /**
     * General util methods for parsing class information
     */
    private static class ClassUtils {

        static String getPathAsString(List<String> pathFromRoot) {
            Iterator<String> i = pathFromRoot.iterator();
            StringBuilder sb = new StringBuilder();
            while(i.hasNext()) {
                sb.append(i.next());
                if ( i.hasNext()) {
                    sb.append(".");
                }
            }
            return sb.toString();
        }

        static Class getCommonSuperclass(Class c1, Class c2) {
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
        private DifferenceType differenceType;

        public Difference(DifferenceType differenceType, String fieldName, String description, Object fieldValue1, Object fieldValue2) {
            this(differenceType, Collections.EMPTY_LIST, fieldName, description, fieldValue1, fieldValue2);
        }

        public Difference(DifferenceType differenceType, List<String> path, String fieldName, String description, Object fieldValue1, Object fieldValue2) {
            this.differenceType = differenceType;
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

        public DifferenceType getDifferenceType() {
            return differenceType;
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
            if (differenceType != that.differenceType) return false;
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
            result = 31 * result + (differenceType != null ? differenceType.hashCode() : 0);
            return result;
        }
    }

    public static enum DifferenceType {
        CYCLE,  //when introspecting field values there are cyclic references which differ between the two objects
        FIELD,  //a field exists for one object in the comparison but is not defined for the other
        CLASS,  //the class of the object returned for a given field differs
        VALUE   //the values for a field differ
    }

    public static class DefaultConfig implements Config {

        public ComparisonFieldType getComparisonFieldType(Field f) {
            //only introspect top level fields by default, no drill down
            return ComparisonFieldType.COMPARISON_FIELD;
        }

        public Comparator getComparator(Field f) {
            return null;
        }

        public FieldIntrospector getFieldIntrospector(List<String> pathFromRoot, Class commonSuperclass, Object o1, Object o2) {
            if ( Map.class.isAssignableFrom(commonSuperclass) ) {
                return new MapIntrospector(pathFromRoot, (Map)o1, (Map)o2);
            } else if (Iterable.class.isAssignableFrom(commonSuperclass)){
                return new IterableIntrospector(pathFromRoot, (Iterable)o1, (Iterable)o2);
            } else {
                return new IdenticalClassFieldIntrospector(pathFromRoot, commonSuperclass, o1, o2);
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

        public SubclassFieldIntrospector(List<String> pathFromRoot, Class commonSuperclass, Object o1, Object o2) {
            super(pathFromRoot);
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

        public SuperclassFieldIntrospector(List<String> pathFromRoot, Class commonSuperclass, Object o1, Object o2) {
            super(pathFromRoot);
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

        public IdenticalClassFieldIntrospector(List<String> pathFromRoot, Class commonSuperclass, Object o1, Object o2) {
            super(pathFromRoot);
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

     public static abstract class ReflectionFieldIntrospector extends AbstractFieldIntrospector {

         public ReflectionFieldIntrospector(List<String> pathFromRoot) {
             super(pathFromRoot);
         }

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
                                return f.getDeclaringClass().isAssignableFrom(o1.getClass()) ? f.get(o1) : UNDEFINED_FIELD;
                            }

                            public String getName() {
                                return f.getName();
                            }

                            public String getPath() {
                                return ReflectionFieldIntrospector.this.getPath(f.getName());
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

    public abstract static class AbstractFieldIntrospector implements FieldIntrospector {

        private String introspectorPath;

        public AbstractFieldIntrospector(List<String> pathFromRoot) {
            this.introspectorPath = ClassUtils.getPathAsString(pathFromRoot);
            if ( introspectorPath.length() > 0) {
                introspectorPath = introspectorPath + ".";
            }
        }

        public String getPath(String fieldName) {
            return introspectorPath + fieldName;
        }
    }

    public static class IterableIntrospector extends AbstractFieldIntrospector {

        private Iterable o1;
        private Iterable o2;

        public IterableIntrospector(List<String> pathFromRoot, Iterable o1, Iterable o2 ) {
            super(pathFromRoot);
            this.o1 = o1;
            this.o2 = o2;
        }

        public List<Field> getFields() {
            final List l1 = getList(o1);
            final List l2 = getList(o2);

            List<Field> result = new ArrayList<Field>();
            int maxSize = Math.max(l1.size(), l2.size());
            for (int loop=0; loop < maxSize; loop++) {
                final int currentIndex = loop;
                Field f = new Field() {

                    public Class<?> getType() {
                        return ClassUtils.getCommonSuperclass(
                            getClassAt(l1, currentIndex),
                            getClassAt(l2, currentIndex)
                        );
                    }

                    private Class getClassAt(List l, int fieldIndex) {
                        Class result = null;
                        if ( l.size() > fieldIndex) {
                            Object o = l.get(fieldIndex);
                            if ( o != null) {
                                result = o.getClass();
                            }
                        }
                        return result;
                    }

                    public Object getValue(Object o) throws Exception {
                        return o == o1 ? getValueAt(l1, currentIndex) : getValueAt(l2, currentIndex);
                    }

                    private Object getValueAt(List l, int fieldIndex) {
                        return ( l.size() > fieldIndex) ? l.get(fieldIndex) : UNDEFINED_FIELD;
                    }

                    public String getName() {
                        return String.valueOf(currentIndex);
                    }

                    public String getPath() {
                        return IterableIntrospector.this.getPath(getName());
                    }
                };
                result.add(f);
            }
            return result;
        }

        private List getList(Iterable i) {
            List l = new ArrayList();
            for ( Object o: i) {
                l.add(o);
            }
            return l;
        }
    }

    /**
     * Map introspector
     */
    public static class MapIntrospector extends AbstractFieldIntrospector {

        private Map o1;
        private Map o2;
        private Stack<Class> classStack1 = new Stack<Class>();
        private Stack<Class> classStack2 = new Stack<Class>();

        public MapIntrospector(List<String> pathFromRoot, Map o1, Map o2) {
            super(pathFromRoot);
            this.o1 = o1;
            this.o2 = o2;
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
                            return doGetValue(o1, key);
                        } else {
                            return doGetValue(o2, key);
                        }
                    }

                    public String getName() {
                        return key.toString();
                    }

                    public String getPath() {
                        return MapIntrospector.this.getPath(getName());
                    }
                });
            }
            return fields;
        }

        private Object doGetValue(Map m, Object key) {
            return m.containsKey(key) ? m.get(key) : UNDEFINED_FIELD;
        }
    }

    /**
     * Defines how the calculator processes the fields
     */
    public static interface Config {

        /**
         * @return a type which indicates whether the value of this field should be compared,
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
        FieldIntrospector getFieldIntrospector(List<String> pathFromRoot, Class c, Object o1, Object o2);

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
        //a value which should be returned if a field is undefined for a given object
        Object UNDEFINED_FIELD = new Object() {
            public String toString() {
                return "Undefined Field";
            }
        };

        List<Field> getFields();
    }

    /**
     * An abstract representation of a Field discovered by introspecting a class/object instance
     */
    public static interface Field {

        /**
         * @return class type for this field, which may be a superclass of the actual instance data
         */
        Class<?> getType();

        /**
         * @return value for this field, which should be the class given by getType(), a subclass of getType() or null
         */
        Object getValue(Object o) throws Exception;

        /**
         * The field names should perferably be unique per class, but duplicate names are tolerated
         * @return Name for this field, used to describe the differences.
         */
        String getName();

        /**
         * @return String representing the full path of this field from the comparison root object
         */
        String getPath();
    }

    public enum ComparisonFieldType {
        COMPARISON_FIELD,
        INTROSPECTION_FIELD,
        IGNORE_FIELD
    }

    private class FieldDiferenceCalculatorException extends RuntimeException {
        public FieldDiferenceCalculatorException(Throwable e) {
            super(e);
        }
    }
}
