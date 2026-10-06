package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.scanner.CSharpMutationScanner;
import mutationtester.scanner.JavaMutationScanner;
import mutationtester.scanner.JavaScriptMutationScanner;
import mutationtester.scanner.PythonMutationScanner;
import mutationtester.scanner.RacketMutationScanner;

class UniversalPitBehaviorTests {

    @TempDir
    Path tempDir;

    private final Map<String, File> sources = new LinkedHashMap<>();

    @BeforeEach
    void createFixtures() throws Exception {
        sources.put("JavaScript", write("pit.js", """
                function condition(flag) { if (flag) return 1; return 0; }
                function negate(flag) { return !flag; }
                function empty() { return "value"; }
                function called() { return compute(); }
                function notifyUser() { notify(); }
                function create() { return new Widget(); }
                """));
        sources.put("Java", write("Pit.java", """
                class Pit {
                    int condition(boolean flag) { if (flag) return 1; return 0; }
                    boolean negate(boolean flag) { return !flag; }
                    String empty() { return "value"; }
                    String called() { return compute(); }
                    void notifyUser() {
                        notifyUserImpl();
                    }
                    Widget create() { return new Widget(); }
                }
                """));
        sources.put("Python", write("pit.py", """
                def condition(flag):
                    if flag:
                        return 1
                    return 0
                def negate(flag):
                    return not flag
                def empty():
                    return "value"
                def called():
                    return compute()
                def notify_user():
                    notify()
                def create():
                    return Widget()
                """));
        sources.put("C#", write("Pit.cs", """
                class Pit {
                    public int Condition(bool flag) { if (flag) return 1; return 0; }
                    public bool Negate(bool flag) { return !flag; }
                    public string Empty() { return "value"; }
                    public string Called() { return Compute(); }
                    public void NotifyUser() {
                        Notify();
                    }
                    public Widget Create() { return new Widget(); }
                }
                """));
        sources.put("Racket", write("pit.rkt", """
                #lang racket
                (define (condition flag) (if flag 1 0))
                (define (negate flag) (not flag))
                (define (empty) (list 1 2))
                (define (called) (compute 1))
                (define (notify-user) (begin (displayln "notice") 1))
                (define (create) (make-widget 1))
                """));
    }

    @Test
    void allFivePitBehaviorsWorkInAllFiveLanguages() throws Exception {
        System.out.println("=== SHARED PIT BEHAVIOR MATRIX ===");
        for (Map.Entry<String, File> language : sources.entrySet()) {
            String name = language.getKey();
            File file = language.getValue();
            System.out.println(name + ":");

            verify(name, file, Mutator.MutatorType.CONDITIONAL_TRUE);
            verify(name, file, Mutator.MutatorType.CONDITIONAL_FALSE);
            verify(name, file, Mutator.MutatorType.REMOVE_LOGICAL_NEGATION);
            verify(name, file, Mutator.MutatorType.EMPTY_RETURNS);
            verify(name, file, Mutator.MutatorType.VML_VOID_METHOD_CALL);
            verify(name, file, Mutator.MutatorType.NON_VOID_METHOD_CALLS);
            verify(name, file, Mutator.MutatorType.CONSTRUCTOR_CALLS);
        }
    }

    @Test
    void checkedInSamplesDemonstrateEverySharedBehavior() throws Exception {
        Map<String, File> samples = new LinkedHashMap<>();
        Path sampleDir = Path.of("samples", "SharedPitProject");
        samples.put("JavaScript", sampleDir.resolve("shared-pit.js").toFile());
        samples.put("Java", sampleDir.resolve("SharedPit.java").toFile());
        samples.put("Python", sampleDir.resolve("shared_pit.py").toFile());
        samples.put("C#", sampleDir.resolve("SharedPit.cs").toFile());
        samples.put("Racket", sampleDir.resolve("shared-pit.rkt").toFile());

        for (Map.Entry<String, File> language : samples.entrySet()) {
            assertTrue(language.getValue().isFile(), "Missing sample for " + language.getKey());
            for (Mutator.MutatorType type : sharedTypes()) {
                verify(language.getKey(), language.getValue(), type);
            }
        }
    }

    @Test
    void regexFallbacksSkipCommentsAndUseCompileSafeJavaDefaults() throws Exception {
        File java = write("ContextSafe.java", """
                class ContextSafe {
                    // if (ignored) return hidden();
                    // return new Hidden();
                    int number() { return calculate(); }
                    boolean enabled() { return check(); }
                    String name() { return loadName(); }
                }
                """);

        assertTrue(scan(java, Mutator.MutatorType.CONDITIONAL_TRUE).isEmpty());
        assertTrue(scan(java, Mutator.MutatorType.CONSTRUCTOR_CALLS).isEmpty());
        List<MutationPoint> calls = scan(java, Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertEquals(List.of("0", "false", "null"),
                calls.stream().map(MutationPoint::getReplacement).toList());
    }

    private List<Mutator.MutatorType> sharedTypes() {
        return List.of(
                Mutator.MutatorType.CONDITIONAL_TRUE,
                Mutator.MutatorType.CONDITIONAL_FALSE,
                Mutator.MutatorType.REMOVE_LOGICAL_NEGATION,
                Mutator.MutatorType.EMPTY_RETURNS,
                Mutator.MutatorType.VML_VOID_METHOD_CALL,
                Mutator.MutatorType.NON_VOID_METHOD_CALLS,
                Mutator.MutatorType.CONSTRUCTOR_CALLS);
    }

    private void verify(String language, File file, Mutator.MutatorType type) throws Exception {
        List<MutationPoint> points = scan(file, type);
        assertFalse(points.isEmpty(), language + " / " + type + " found no mutations");
        MutationPoint point = points.get(0);
        if (type == Mutator.MutatorType.CONDITIONAL_TRUE
                || type == Mutator.MutatorType.CONDITIONAL_FALSE) {
            assertEquals(expectedConditional(language, type), point.getReplacement(),
                    language + " used an invalid conditional literal");
        }
        for (MutationPoint candidate : points) {
            String originalSource = Files.readString(file.toPath());
            String mutated = apply(originalSource, candidate);
            assertFalse(mutated.equals(originalSource), language + " / " + type + " did not change source");
        }
        System.out.println("  [PASS] " + type + ": " + printable(point.getOperator())
                + " -> " + printable(point.getReplacement())
                + (points.size() > 1 ? " (" + points.size() + " valid points)" : ""));
    }

    private String expectedConditional(String language, Mutator.MutatorType type) {
        boolean trueMutation = type == Mutator.MutatorType.CONDITIONAL_TRUE;
        if (language.equals("Python")) return trueMutation ? "True" : "False";
        if (language.equals("Racket")) return trueMutation ? "#t" : "#f";
        return trueMutation ? "true" : "false";
    }

    private File write(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path.toFile();
    }

    private List<MutationPoint> scan(File file, Mutator.MutatorType type) throws Exception {
        String name = file.getName();
        if (name.endsWith(".js")) return JavaScriptMutationScanner.scanFile(file, type);
        if (name.endsWith(".java")) return JavaMutationScanner.scanFile(file, type);
        if (name.endsWith(".py")) return PythonMutationScanner.scanFile(file, type);
        if (name.endsWith(".cs")) return CSharpMutationScanner.scanFile(file, type);
        return RacketMutationScanner.scanFile(file, type);
    }

    private String apply(String source, MutationPoint point) throws Exception {
        Method method = Main.class.getDeclaredMethod("applyMutation", String.class, MutationPoint.class);
        method.setAccessible(true);
        return (String) method.invoke(null, source, point);
    }

    private String printable(String value) {
        return value.isEmpty() ? "<removed>" : value;
    }
}
