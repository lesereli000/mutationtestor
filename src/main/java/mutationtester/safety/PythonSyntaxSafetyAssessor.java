package mutationtester.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import mutationtester.PythonLexer;
import mutationtester.PythonParser;
import mutationtester.model.MutationPoint;

/** Limited intrafile structural analysis. Missing bindings/call targets stay unknown. */
public final class PythonSyntaxSafetyAssessor {
    public SafetyAssessment assess(MutationPoint point, String original, String mutated) {
        if (point == null || original == null || mutated == null || original.equals(mutated)) {
            return unknown("The original and mutated source cannot be compared.");
        }
        ParseTree tree = parse(original);
        if (tree == null || parse(mutated) == null) {
            return unknown("ANTLR could not parse both versions; effects cannot be established.");
        }
        List<ParserRuleContext> ancestors = new ArrayList<>();
        findAncestors(tree, point.getLineNumber(), point.getColumnNumber(), ancestors);
        if (ancestors.isEmpty()) return unknown("The mutation location could not be mapped to a syntax node.");
        Map<String, List<PythonParser.FuncdefContext>> functions = new HashMap<>();
        collectFunctions(tree, functions);

        for (int i = ancestors.size() - 1; i >= 0; i--) {
            ParserRuleContext node = ancestors.get(i);
            if (node instanceof PythonParser.While_stmtContext loop && contains(loop.test(), point)) {
                return assessment("RESOURCE_EXHAUSTION", "The changed expression is the condition of a while loop at line "
                        + loop.getStart().getLine() + "; it may change termination or iteration count.");
            }
            if (node instanceof PythonParser.For_stmtContext loop && contains(loop.testlist(), point)) {
                return assessment("RESOURCE_EXHAUSTION", "The changed expression determines the iterable of a for loop at line "
                        + loop.getStart().getLine() + "; it may change iteration count.");
            }
            if (node instanceof PythonParser.If_stmtContext branch && contains(branch.cond, point)) {
                SafetyAssessment risk = branchAssessment(branch, "if", branch.getStart().getLine(), functions);
                if (risk != null) return risk;
                if (hasExit(branch)) {
                    for (int parent = i - 1; parent >= 0; parent--) {
                        if (ancestors.get(parent) instanceof PythonParser.FuncdefContext function) {
                            SafetyAssessment later = afterGuardAssessment(function.suite(), branch, functions);
                            if (later != null) return later;
                            break;
                        }
                    }
                }
                return unknown("The mutation changes an if condition. No modeled side-effect call was found in its branch; other effects remain possible.");
            }
            if (node instanceof PythonParser.Elif_clauseContext branch && contains(branch.test(), point)) {
                SafetyAssessment risk = branchAssessment(branch.suite(), "elif", branch.getStart().getLine(), functions);
                if (risk != null) return risk;
                return unknown("The mutation changes an elif condition; its effects are unresolved.");
            }
            if (node instanceof PythonParser.ExprContext expr && isCall(expr)) {
                String name = callName(expr);
                String category = category(name);
                if (category != null) return assessment(category, "The mutation occurs within the call to " + name
                        + " at line " + expr.getStart().getLine() + "; it may change the operation or its arguments.");
                return unknown("The mutation occurs within the call to " + name + "; its effects could not be resolved.");
            }
        }
        return unknown("The syntax tree does not establish the effects of this mutation; calls, state and other files may matter.");
    }

    private static SafetyAssessment branchAssessment(ParseTree body, String kind, int line, Map<String, List<PythonParser.FuncdefContext>> functions) {
        List<PythonParser.ExprContext> calls = new ArrayList<>();
        collectCalls(body, calls);
        for (PythonParser.ExprContext call : calls) {
            String name = callName(call);
            Finding finding = trace(call, functions, new HashSet<>(), 0);
            if (finding != null) {
                return assessment("CONTROL_FLOW_" + finding.category(), "The changed " + kind + " condition at line " + line
                        + " controls the call to " + name + " at line " + call.getStart().getLine() + ". "
                        + (functions.containsKey(name) ? "Possible same-file path: " : "") + finding.evidence()
                        + " Call names and possible helper matches are clues; bindings are not verified.");
            }
        }
        return null;
    }

    private record Finding(String category, String evidence) {}

    private static SafetyAssessment afterGuardAssessment(ParseTree functionBody, ParserRuleContext guard,
            Map<String, List<PythonParser.FuncdefContext>> functions) {
        List<PythonParser.ExprContext> calls = new ArrayList<>();
        collectCalls(functionBody, calls);
        for (PythonParser.ExprContext call : calls) {
            if (call.getStart().getTokenIndex() <= guard.getStop().getTokenIndex()) continue;
            Finding finding = trace(call, functions, new HashSet<>(), 0);
            if (finding != null) return assessment("GUARD_CLAUSE_" + finding.category(),
                    "The changed condition at line " + guard.getStart().getLine()
                    + " contains an early exit; changing it may alter whether a later call is reached. "
                    + finding.evidence() + " Call names and possible helper matches are clues; bindings and execution paths are not verified.");
        }
        return null;
    }

    private static boolean hasExit(ParseTree tree) {
        if (tree instanceof PythonParser.FuncdefContext || tree instanceof PythonParser.ClassdefContext) return false;
        if (tree instanceof PythonParser.Return_stmtContext || tree instanceof PythonParser.Raise_stmtContext) return true;
        for (int i = 0; i < tree.getChildCount(); i++) if (hasExit(tree.getChild(i))) return true;
        return false;
    }

    private static Finding trace(PythonParser.ExprContext call, Map<String, List<PythonParser.FuncdefContext>> functions,
            Set<String> active, int depth) {
        String name = callName(call);
        List<PythonParser.FuncdefContext> matches = functions.get(name);
        if (!name.contains(".") && matches != null) {
            if (matches.size() != 1 || depth >= 4 || !active.add(name)) return null;
            try {
                List<PythonParser.ExprContext> calls = new ArrayList<>();
                collectCalls(matches.get(0).suite(), calls);
                for (PythonParser.ExprContext nested : calls) {
                    Finding found = trace(nested, functions, active, depth + 1);
                    if (found != null) return new Finding(found.category(), name + " -> " + found.evidence());
                }
                return null;
            } finally {
                active.remove(name);
            }
        }
        String category = category(name);
        return category == null ? null : new Finding(category, name + " (line " + call.getStart().getLine() + ") suggests a " + category.toLowerCase(Locale.ROOT) + " operation.");
    }

    private static void collectFunctions(ParseTree tree, Map<String, List<PythonParser.FuncdefContext>> functions) {
        if (tree instanceof PythonParser.ClassdefContext) return;
        if (tree instanceof PythonParser.FuncdefContext function) {
            functions.computeIfAbsent(function.name().getText(), ignored -> new ArrayList<>()).add(function);
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++) collectFunctions(tree.getChild(i), functions);
    }

    private static void collectCalls(ParseTree tree, List<PythonParser.ExprContext> calls) {
        if (tree instanceof PythonParser.FuncdefContext || tree instanceof PythonParser.ClassdefContext) return;
        if (tree instanceof PythonParser.ExprContext expr && isCall(expr)) calls.add(expr);
        for (int i = 0; i < tree.getChildCount(); i++) collectCalls(tree.getChild(i), calls);
    }

    private static boolean isCall(PythonParser.ExprContext expr) {
        return expr.atom() != null && expr.trailer().stream().anyMatch(t -> t.arguments() != null
                && t.arguments().getStart().getText().equals("("));
    }

    private static String callName(PythonParser.ExprContext expr) {
        StringBuilder name = new StringBuilder(expr.atom().getText());
        for (PythonParser.TrailerContext trailer : expr.trailer()) {
            if (trailer.DOT() != null && trailer.name() != null) name.append('.').append(trailer.name().getText());
            if (trailer.arguments() != null && trailer.arguments().getStart().getText().equals("(")) break;
        }
        return name.toString();
    }

    private static String category(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        String tail = lower.substring(lower.lastIndexOf('.') + 1);
        return switch (tail) {
            case "execute", "executemany", "commit" -> "DATABASE";
            case "open", "unlink", "rmtree", "remove", "write", "write_text", "write_bytes" -> "FILESYSTEM";
            case "fetch", "request", "urlopen", "connect", "send", "sendall" -> "NETWORK";
            case "popen", "system", "exec", "spawn", "run" -> "PROCESS";
            default -> null;
        };
    }

    private static void findAncestors(ParseTree tree, int line, int column, List<ParserRuleContext> out) {
        if (!(tree instanceof ParserRuleContext ctx) || !contains(ctx, line, column)) return;
        out.add(ctx);
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof ParserRuleContext rule && contains(rule, line, column)) {
                findAncestors(rule, line, column, out);
                break;
            }
        }
    }

    private static boolean contains(ParserRuleContext ctx, MutationPoint point) {
        return ctx != null && contains(ctx, point.getLineNumber(), point.getColumnNumber());
    }

    private static boolean contains(ParserRuleContext ctx, int line, int col) {
        Token start = ctx.getStart(), stop = ctx.getStop();
        if (start == null || stop == null || line < start.getLine() || line > stop.getLine()) return false;
        if (line == start.getLine() && col < start.getCharPositionInLine()) return false;
        int length = stop.getText() == null ? 1 : stop.getText().length();
        return line != stop.getLine() || col < stop.getCharPositionInLine() + length;
    }

    private static ParseTree parse(String source) {
        PythonLexer lexer = new PythonLexer(CharStreams.fromString(source));
        ErrorCounter errors = new ErrorCounter();
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        PythonParser parser = new PythonParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        ParseTree root = parser.root();
        return errors.count == 0 ? root : null;
    }

    private static SafetyAssessment unknown(String reason) {
        return new SafetyAssessment(SafetyLevel.UNKNOWN, "UNRESOLVED_CONTEXT", reason, 0, "ANTLR Python syntax analysis");
    }

    private static SafetyAssessment assessment(String category, String reason) {
        return new SafetyAssessment(SafetyLevel.REVIEW, category, reason, 0, "ANTLR Python syntax analysis");
    }

    private static final class ErrorCounter extends BaseErrorListener {
        int count;
        @Override public void syntaxError(Recognizer<?, ?> recognizer, Object symbol, int line, int column,
                String message, RecognitionException exception) { count++; }
    }
}
