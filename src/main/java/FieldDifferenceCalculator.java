import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by IntelliJ IDEA.
 * User: Nick Ebbutt
 * Date: 14-Jul-2009
 * Time: 13:38:06
 *
 * A class which compares two Objects to produce a list of differences.
 * It can compare fields at top level, or introspect further down the object graph 
 *
 * When you create a FieldDifferenceCalculator you pass in a config which defines which fields are considered for the comparison
 * and which fields are introspected further.
 *
 * The default config compares fields at the top level only (no drill down to introspect further down the object graph)
 * To configure the calculator to introspect further down the graph you have to pass in a Config object,
 * in which you can define for each field:
 * --> whether it should be compared, introspected further, or ignored
 * --> optionally a comparator to do the comparison
 * The above can be based on the class type, the field name or the path of the field from the comparison root object
 *
 * A 'Field' does not necessarily imply a field on a class at language level, although the default introspector does use reflection
 * to find the fields defined by the class of the objects being compared.
 *
 * FieldDifferenceCalculator has it's own Field abstraction which makes it possible for introspectors to work at a higher level
 * For example, it is possible for an Introspector to return a list of the values in a Map as Fields, where the fields are
 * identified by the keys in the Map. This allows introspection/comparison of Map instances as part of the graph.
 */
public class FieldDifferenceCalculator {

    public static final String INPUT_OBJECT_TEXT = "";

    private static Config DEFAULT_CONFIG = new DefaultConfig();

    private List<String> path;
    private String pathAsString;
    private volatile String description1;
    private volatile String description2;
    private List<Object> visitedNodes1;
    private List<Object> visitedNodes2;
    private Config config;

    public FieldDifferenceCalculator() {
        this(DEFAULT_CONFIG);
    }

    public FieldDifferenceCalculator(Config config) {
        this("object1", "object2", config);
    }

    public FieldDifferenceCalculator(String description1, String description2) {
        this(description1, description2, DEFAULT_CONFIG, Collections.EMPTY_LIST, new ArrayList<Object>(), new ArrayList<Object>());
    }

    public FieldDifferenceCalculator(String description1, String description2, Config config) {
        this(description1, description2, config, Collections.EMPTY_LIST, new ArrayList<Object>(), new ArrayList<Object>());
    }

    private FieldDifferenceCalculator(String description1, String description2, Config config, List<String> path, List<Object> visitedNodes1, List<Object> visitedNodes2) {
        this.config = config;
        this.path = path;
        this.description1 = description1;
        this.description2 = description2;
        this.visitedNodes1 = visitedNodes1;
        this.visitedNodes2 = visitedNodes2;
        this.pathAsString = ClassUtils.getPathAsString(path);
    }

    public void setDescription1(String description1) {
        this.description1 = description1;
    }

    public void setDescription2(String description2) {
        this.description2 = description2;
    }

    public FieldDifferenceCalculator ignorePath(String path) {
        config.ignorePath(path);
        return this;
    }

    public FieldDifferenceCalculator introspectPath(String path) {
        config.introspectPath(path);
        return this;
    }

    public FieldDifferenceCalculator introspectPath(String path, IntrospectorFactory f) {
        config.introspectPath(path, f);
        return this;
    }

    public void printDifferences(Object o1, Object o2) {
        printDifferences(o1, o2, System.out);
    }

    public void printDifferences(Object o1, Object o2, Appendable s) {
        List<Difference> differences = getDifferences(o1, o2);
        try {
            for ( Difference d : differences) {
                s.append(d.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        FieldIntrospector i = config.getFieldIntrospector(pathAsString, clazz, o1, o2);
        List<Field> fields = i.getFields();
        for ( Field f : fields) {
            switch ( config.getCalculatorFieldType(f)) {
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
                    config,
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
            Comparator c = config.getComparator(f);
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

        static List getList(Object array) {
            List result = null;
            //this is horrible, is there a better way?
            //We could use reflection to create the List but that would be slower.
            if ( array instanceof Object[] ) {
                result = Arrays.asList((Object[])array);
            } else {
                result = new ArrayList();
                if ( array instanceof int[] ) {
                    for ( int i : (int[]) array) {
                        result.add(i);
                    }
                } else if ( array instanceof float[] ) {
                    for ( float f : (float[]) array) {
                        result.add(f);
                    }
                } else if ( array instanceof byte[] ) {
                    for ( byte f : (byte[]) array) {
                        result.add(f);
                    }
                } else if ( array instanceof char[] ) {
                    for ( char f : (char[]) array) {
                        result.add(f);
                    }
                } else if ( array instanceof double[] ) {
                    for ( double f : (double[]) array) {
                        result.add(f);
                    }
                } else if ( array instanceof short[] ) {
                    for ( short f : (short[]) array) {
                        result.add(f);
                    }
                } else if ( array instanceof long[] ) {
                    for ( long f : (long[]) array) {
                        result.add(f);
                    }
                } else if ( array instanceof boolean[] ) {
                    for ( boolean f : (boolean[]) array) {
                        result.add(f);
                    }
                } else {
                    throw new UnsupportedOperationException("Cannot convert array to List, argument was not an array instance");
                }
            }
            return result;
        }

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

        private static final ArrayAsListComparator arrayAsListComparator = new ArrayAsListComparator();
        private List<Pattern> ignorePatterns = new ArrayList<Pattern>();
        private Map<String, Boolean> ignorePaths = new HashMap<String, Boolean>();
        private List<Pattern> introspectPatterns = new ArrayList<Pattern>();
        private Map<String, Boolean> introspectPaths = new HashMap<String, Boolean>();
        private Map<Pattern, IntrospectorFactory> patternToIntrospector = new ConcurrentHashMap<Pattern, IntrospectorFactory>();
        private Map<String, IntrospectorFactory> pathToIntrospector = new ConcurrentHashMap<String, IntrospectorFactory>();

        public CalculatorFieldType getCalculatorFieldType(Field f) {
            CalculatorFieldType result;
            if (isIgnoreField(f)) {
                result = CalculatorFieldType.IGNORE_FIELD;
            } else {
                result = isIntrospectField(f) ? CalculatorFieldType.INTROSPECTION_FIELD : CalculatorFieldType.COMPARISON_FIELD;
            }
            return result;
        }

        protected boolean isIgnoreField(Field f) {
            Boolean result = ignorePaths.get(f.getPath());
            //do we already know wether to ignore this field?, if not see if it matches a pattern
            if ( result == null) {
                result = false;
                for ( Pattern p : ignorePatterns) {
                    result = (p.matcher(f.getPath()).matches());
                    if ( result ) {
                        break;
                    }
                }
                ignorePaths.put(f.getPath(), result);
            }
            return result;
        }

        protected boolean isIntrospectField(Field f) {
            String path = f.getPath();
            Boolean result = introspectPaths.get(path);
            //do we already know wether to introspect this field?, if not see if it matches a pattern
            if ( result == null) {
                result = false;
                for ( Pattern p : introspectPatterns) {
                    result = (p.matcher(path).matches());
                    if ( result ) {
                        //is there a specific introspector defined to use?
                        if (patternToIntrospector.containsKey(p)) {
                            pathToIntrospector.put(path, patternToIntrospector.get(p));
                        }
                        break;
                    }
                }
                introspectPaths.put(path, result);
            }
            return result;
        }

        protected IntrospectorFactory getIntrospectorFactory(String fieldPath) {
            return pathToIntrospector.get(fieldPath);
        }

        public Comparator getComparator(Field f) {
            Comparator result = null;
            if ( f.getType().isArray()) {
                return arrayAsListComparator;
            }
            return result;
        }

        public FieldIntrospector getFieldIntrospector(String fieldPath, Class commonSuperclass, Object o1, Object o2) {
            IntrospectorFactory f = getIntrospectorFactory(fieldPath);
            return f != null ?
                    f.getFieldIntrospector(fieldPath, commonSuperclass, o1, o2) :
                    getDefaultFieldIntrospector(fieldPath, commonSuperclass, o1, o2);
        }

        public FieldIntrospector getDefaultFieldIntrospector(String fieldPath, Class commonSuperclass, Object o1, Object o2) {
            if ( Map.class.isAssignableFrom(commonSuperclass) ) {
                return new MapIntrospector(fieldPath, (Map)o1, (Map)o2);
            } else if (Iterable.class.isAssignableFrom(commonSuperclass)){
                return new IterableIntrospector(fieldPath, (Iterable)o1, (Iterable)o2);
            } else if (commonSuperclass.isArray()) {
                return new ArrayIntrospector(fieldPath, o1, o2);
            } else {
                //an introspector which includes differences for fields which are only present in one of the two input objects
                return new SubclassFieldIntrospector(fieldPath, commonSuperclass, o1, o2);
            }
        }

        public Config ignorePath(String path) {
            clearIgnoreMaps();
            ignorePatterns.add(Pattern.compile(path));
            return this;
        }

        public Config introspectPath(String path) {
            clearIntrospectionMaps();
            introspectPatterns.add(Pattern.compile(path));
            return this;
        }

        public Config introspectPath(String path, IntrospectorFactory f) {
            clearIntrospectionMaps();
            Pattern p = Pattern.compile(path);
            introspectPatterns.add(p);
            patternToIntrospector.put(p, f);
            return this;
        }
        
        //ignore patterns are changing, clear our precomputed field maps
        private void clearIgnoreMaps() {
            ignorePaths.clear();
        }

        //introspection patterns are changing, clear our precomputed field maps
        private void clearIntrospectionMaps() {
            introspectPaths.clear();
            pathToIntrospector.clear();
        }

    }

    public interface IntrospectorFactory {
        FieldIntrospector getFieldIntrospector(String fieldPath, Class commonSuperclass, Object o1, Object o2);
    }

    private static class ArrayAsListComparator implements Comparator {
        public int compare(Object o1, Object o2) {
            int result = 0;
            if ( o1 != o2) {
                List l1 = ClassUtils.getList(o1);
                List l2 = ClassUtils.getList(o2);
                result = l1.equals(l2) ? 0 : l1.size() >= l2.size() ? -1 : 1;
            }
            return result;
        }
    }

    /**
     * Includes differences for subclass fields which are not shared
     */
    public static class SubclassFieldIntrospector extends ReflectionFieldIntrospector {
        private Class commonSuperclass;
        private Class o1;
        private Class o2;

        public SubclassFieldIntrospector(String pathFromRoot, Class commonSuperclass, Object o1, Object o2) {
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
     * This class introspects fields from the common superclass upwards
     * Fields at a subclass level (which are not shared) are not included
     */
    public static class SuperclassFieldIntrospector extends ReflectionFieldIntrospector {
        private Class commonSuperclass;

        public SuperclassFieldIntrospector(String path, Class commonSuperclass, Object o1, Object o2) {
            super(path);
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
    * If the class type differs no fields will be returned
    */
    public static class IdenticalClassFieldIntrospector extends ReflectionFieldIntrospector {

        private Class commonSuperclass;
        private Class o1;
        private Class o2;

        public IdenticalClassFieldIntrospector(String path, Class commonSuperclass, Object o1, Object o2) {
            super(path);
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

         public ReflectionFieldIntrospector(String path) {
             super(path);
         }

         //find all the fields for this class and superclasses
         protected void addFieldsRecursivelyUntilReachingClass(List<Field> result, Class c, Class endClass) {
            if ( c != endClass ) {
                java.lang.reflect.Field[] fields = c.getDeclaredFields();
                for ( final java.lang.reflect.Field f : fields) {
                    if ( ! isIgnoreField(f) ) {
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

        protected boolean isIgnoreField(java.lang.reflect.Field f) {
            return Modifier.isStatic(f.getModifiers());
        }
    }

    public abstract static class AbstractFieldIntrospector implements FieldIntrospector {

        private String introspectorPath;

        public AbstractFieldIntrospector(String pathFromRoot) {
            introspectorPath = pathFromRoot.length() > 0 ? pathFromRoot + "." : "";
        }

        public String getPath(String fieldName) {
            return introspectorPath + fieldName;
        }
    }

    public static class AbstractListIntrospector extends AbstractFieldIntrospector {

        private List list1;
        private Object o1;
        private List list2;
        private Object o2;

        /**
         * @param o1, an Iterable instance or an array
         * @param o2, an Iterable instance or an array
         */
        public AbstractListIntrospector(String path, List list1, Object o1, List list2, Object o2 ) {
            super(path);
            this.list1 = list1;
            this.o1 = o1;
            this.list2 = list2;
            this.o2 = o2;
        }

        public List<Field> getFields() {
            List<Field> result = new ArrayList<Field>();
            int maxSize = Math.max(list1.size(), list2.size());
            for (int loop=0; loop < maxSize; loop++) {
                final int currentIndex = loop;
                Field f = new Field() {

                    public Class<?> getType() {
                        return ClassUtils.getCommonSuperclass(
                            getClassAt(list1, currentIndex),
                            getClassAt(list2, currentIndex)
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
                        return o == o1 ? getValueAt(list1, currentIndex) : getValueAt(list2, currentIndex);
                    }

                    private Object getValueAt(List l, int fieldIndex) {
                        return ( l.size() > fieldIndex) ? l.get(fieldIndex) : UNDEFINED_FIELD;
                    }

                    public String getName() {
                        return String.valueOf(currentIndex);
                    }

                    public String getPath() {
                        return AbstractListIntrospector.this.getPath(getName());
                    }
                };
                result.add(f);
            }
            return result;
        }
    }

    public static class ArrayIntrospector extends AbstractListIntrospector {

        public ArrayIntrospector(String path, Object o1, Object o2 ) {
            super(path, ClassUtils.getList(o1), o1, ClassUtils.getList(o2), o2);
        }
    }

    public static class IterableIntrospector extends AbstractListIntrospector {

        public IterableIntrospector(String path, Iterable o1, Iterable o2 ) {
            super(path, getList(o1), o1, getList(o2), o2);
        }

        private static List getList(Iterable i) {
            List result;
            if ( i instanceof List && i instanceof RandomAccess ) {
                result = (List)i;
            } else if ( i instanceof Collection ) {
                result = new ArrayList((Collection)i);
            } else {
                result = new ArrayList();
                for ( Object o: i) {
                    result.add(o);
                }
            }
            return result;
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

        public MapIntrospector(String path, Map o1, Map o2) {
            super(path);
            this.o1 = o1;
            this.o2 = o2;
        }

        public List<Field> getFields() {
            List<Object> allKeys = new ArrayList<Object>();
            //we want to process fields for the superset of all keys in the maps but provide predictable ordering for
            //SortedMap instances, otherwise testing is hard. We can't guarantee keys from both maps are comparable so
            //can't sort the superset, but we can use a List to at least provide a predictable result
            allKeys.addAll(o1.keySet());
            allKeys.addAll(o2.keySet());

            Set<Object> keysProcessed = new HashSet<Object>();
            List<Field> fields = new ArrayList<Field>();
            for ( final Object key : allKeys) {
                if ( ! keysProcessed.contains(key)) {
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
                keysProcessed.add(key);
            }
            return fields;
        }

        private Object doGetValue(Map m, Object key) {
            return m.containsKey(key) ? m.get(key) : UNDEFINED_FIELD;
        }
    }

    /**
     * Defines how the calculator processes fields
     */
    public static interface Config {

        /**
         * @return a type which indicates whether the value of this field should be compared,
         * whether we should introspect it to drill down further, or ignore it
         */
        CalculatorFieldType getCalculatorFieldType(Field f);

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
         * which are either instances of Class commonSuperclass, or of subclasses of commonSuperclass
         */
        FieldIntrospector getFieldIntrospector(String path, Class commonSuperclass, Object o1, Object o2);

        /**
         * Request that the calculator ignores fields with paths matching the path pattern provided
         */
        Config ignorePath(String path);

        /**
         * Request that the calculator introspects fields with paths matching the path pattern provided, using a default introspector
         */
        Config introspectPath(String path);

        /**
         * Request that the calculator introspects fields with paths matching the path pattern provided 
         * using the supplied introspector factory to create an introspector instance
         */
        Config introspectPath(String path, IntrospectorFactory f);
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

    public enum CalculatorFieldType {
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
