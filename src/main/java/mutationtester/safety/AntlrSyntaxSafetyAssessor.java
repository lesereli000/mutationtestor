package mutationtester.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import mutationtester.*;
import mutationtester.model.MutationPoint;

/** Syntax-based, intrafile triage. Call names are clues, not resolved bindings. */
public final class AntlrSyntaxSafetyAssessor {
    public SafetyAssessment assess(MutationPoint point, String before, String after) {
        String name = point.getSourceFile().getName().toLowerCase(Locale.ROOT);
        String language = name.endsWith(".java") ? "Java" : name.endsWith(".js") ? "JavaScript"
                : name.endsWith(".cs") ? "C#" : name.endsWith(".rkt") ? "Racket" : "";
        if (language.isEmpty() || before == null || after == null || before.equals(after)) return unknown(language, "Unsupported language or invalid change.");
        ParseTree original = parse(language, before);
        if (original == null || parse(language, after) == null) return unknown(language, "ANTLR could not parse both versions.");
        List<ParserRuleContext> ancestors = new ArrayList<>();
        find(original, point, ancestors);
        if (ancestors.isEmpty()) return unknown(language, "The mutation could not be mapped to a syntax node.");
        Map<String, List<JavaScriptParser.FunctionDeclarationContext>> functions = new HashMap<>();
        if (language.equals("JavaScript")) collectJsFunctions(original, functions);
        for (int i = ancestors.size() - 1; i >= 0; i--) {
            ParserRuleContext node = ancestors.get(i);
            Guard guard = guard(node, point);
            if (guard == null) continue;
            if (guard.loop) return review(language, "RESOURCE_EXHAUSTION", "The mutation changes a loop condition at line " + node.getStart().getLine() + "; it may change termination or iteration count.");
            List<Token> tokens = new ArrayList<>();
            leaves(node, tokens);
            Finding finding = findRisk(tokens, language, functions, new HashSet<>(), 0, -1);
            if (finding != null) return review(language, "CONTROL_FLOW_" + finding.category(),
                    "The changed condition at line " + node.getStart().getLine() + " controls a branch with a possible call path: "
                            + finding.path() + ". Bindings and execution paths are not verified.");
            if (language.equals("JavaScript") && hasReturn(node)) {
                for (int parent = i - 1; parent >= 0; parent--) {
                    if (ancestors.get(parent) instanceof JavaScriptParser.FunctionDeclarationContext function) {
                        List<Token> later = new ArrayList<>(); leaves(function.functionBody(), later);
                        Finding next = findRisk(later, language, functions, new HashSet<>(), 0, node.getStop().getTokenIndex());
                        if (next != null) return review(language, "GUARD_CLAUSE_" + next.category(),
                                "The changed condition at line " + node.getStart().getLine() + " may alter an early exit before possible call path: "
                                        + next.path() + ". Bindings and execution paths are not verified.");
                        break;
                    }
                }
            }
            return unknown(language, "The changed condition has no recognized operation in its parsed branch; other effects may exist.");
        }
        return unknown(language, "The syntax tree does not establish the mutation's effects; calls, bindings, and other files may matter.");
    }

    private record Guard(boolean loop) {}

    private record Finding(String category, String path) {}

    private static Finding findRisk(List<Token> tokens, String language,
            Map<String, List<JavaScriptParser.FunctionDeclarationContext>> functions, Set<String> active, int depth, int afterIndex) {
        for (int j = 0; j < tokens.size(); j++) {
            Token token = tokens.get(j);
            if (token.getTokenIndex() <= afterIndex) continue;
            String name = token.getText();
            boolean call = language.equals("Racket") ? j > 0 && tokens.get(j - 1).getText().equals("(")
                    : j + 1 < tokens.size() && tokens.get(j + 1).getText().equals("(");
            if (!call) continue;
            List<JavaScriptParser.FunctionDeclarationContext> matches = functions.get(name);
            boolean qualified = j > 0 && tokens.get(j - 1).getText().equals(".");
            if (!qualified && matches != null) {
                if (matches.size() != 1 || depth >= 4 || !active.add(name)) continue;
                try {
                    List<Token> nested = new ArrayList<>(); leaves(matches.get(0).functionBody(), nested);
                    Finding found = findRisk(nested, language, functions, active, depth + 1, -1);
                    if (found != null) return new Finding(found.category(), name + " -> " + found.path());
                } finally { active.remove(name); }
                continue;
            }
            String category = category(name);
            if (category != null) return new Finding(category, name + " (line " + token.getLine() + ")");
        }
        return null;
    }

    private static void collectJsFunctions(ParseTree tree, Map<String, List<JavaScriptParser.FunctionDeclarationContext>> functions) {
        if (tree instanceof JavaScriptParser.FunctionDeclarationContext function) {
            if (function.identifier() != null) functions.computeIfAbsent(function.identifier().getText(), ignored -> new ArrayList<>()).add(function);
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++) collectJsFunctions(tree.getChild(i), functions);
    }

    private static boolean hasReturn(ParseTree tree) {
        if (tree instanceof JavaScriptParser.FunctionDeclarationContext) return false;
        if (tree instanceof JavaScriptParser.ReturnStatementContext) return true;
        for (int i = 0; i < tree.getChildCount(); i++) if (hasReturn(tree.getChild(i))) return true;
        return false;
    }
    private static Guard guard(ParserRuleContext node, MutationPoint p) {
        if (node instanceof JavaParser.StatementContext n) {
            if (n.IF() != null && !n.expression().isEmpty() && contains(n.expression(0), p)) return new Guard(false);
            if (n.WHILE() != null && !n.expression().isEmpty() && contains(n.expression(0), p)) return new Guard(true);
            if (n.FOR() != null && contains(n.forControl(), p)) return new Guard(true);
        }
        if (node instanceof JavaScriptParser.IfStatementContext n && contains(n.expressionSequence(), p)) return new Guard(false);
        if (node instanceof JavaScriptParser.WhileStatementContext n && contains(n.expressionSequence(), p)) return new Guard(true);
        if (node instanceof JavaScriptParser.ForStatementContext n && n.expressionSequence().stream().anyMatch(x -> contains(x, p))) return new Guard(true);
        if (node instanceof CSharpParser.If_statementContext n && contains(n.boolean_expression(), p)) return new Guard(false);
        if (node instanceof CSharpParser.While_statementContext n && contains(n.boolean_expression(), p)) return new Guard(true);
        if (node instanceof CSharpParser.For_statementContext n && contains(n.for_condition(), p)) return new Guard(true);
        if (node instanceof ISLParser.ExprContext n && n.getChildCount() > 3 && n.getChild(0).getText().equals("(")
                && n.getChild(1).getText().equals("if") && !n.expr().isEmpty() && contains(n.expr(0), p)) return new Guard(false);
        return null;
    }

    private static String category(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "execute", "executeupdate", "executemany", "commit", "query" -> "DATABASE";
            case "open", "delete", "deletefile", "delete-file", "delete-directory", "write", "writealltext", "writeallbytes", "open-output-file", "call-with-output-file" -> "FILESYSTEM";
            case "fetch", "request", "connect", "send", "sendasync" -> "NETWORK";
            case "exec", "start", "startprocess", "spawn", "system" -> "PROCESS";
            default -> null;
        };
    }

    private static void leaves(ParseTree tree, List<Token> tokens) {
        if (tree instanceof TerminalNode terminal) { tokens.add(terminal.getSymbol()); return; }
        if (tree instanceof JavaParser.MethodDeclarationContext || tree instanceof JavaScriptParser.FunctionDeclarationContext
                || tree instanceof CSharpParser.Method_declarationContext || tree instanceof ISLParser.DefinitionContext) return;
        for (int i = 0; i < tree.getChildCount(); i++) leaves(tree.getChild(i), tokens);
    }

    private static void find(ParseTree tree, MutationPoint p, List<ParserRuleContext> out) {
        if (!(tree instanceof ParserRuleContext ctx) || !contains(ctx, p)) return;
        out.add(ctx);
        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (ctx.getChild(i) instanceof ParserRuleContext child && contains(child, p)) { find(child, p, out); break; }
        }
    }

    private static boolean contains(ParserRuleContext ctx, MutationPoint p) {
        if (ctx == null || ctx.getStart() == null || ctx.getStop() == null) return false;
        Token a = ctx.getStart(), b = ctx.getStop(); int line = p.getLineNumber(), col = p.getColumnNumber();
        if (line < a.getLine() || line > b.getLine() || (line == a.getLine() && col < a.getCharPositionInLine())) return false;
        return line != b.getLine() || col < b.getCharPositionInLine() + (b.getText() == null ? 1 : b.getText().length());
    }

    private static ParseTree parse(String language, String source) {
        CharStream input = CharStreams.fromString(source);
        Lexer lexer = switch (language) {
            case "Java" -> new JavaLexer(input); case "JavaScript" -> new JavaScriptLexer(input);
            case "C#" -> new CSharpLexer(input); case "Racket" -> new ISLLexer(input); default -> null;
        };
        if (lexer == null) return null;
        Errors errors = new Errors(); lexer.removeErrorListeners(); lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        Parser parser = switch (language) {
            case "Java" -> new JavaParser(tokens); case "JavaScript" -> new JavaScriptParser(tokens);
            case "C#" -> new CSharpParser(tokens); case "Racket" -> new ISLParser(tokens); default -> throw new IllegalStateException();
        };
        parser.removeErrorListeners(); parser.addErrorListener(errors);
        ParseTree root = switch (language) {
            case "Java" -> ((JavaParser) parser).compilationUnit(); case "JavaScript" -> ((JavaScriptParser) parser).program();
            case "C#" -> ((CSharpParser) parser).prog(); default -> ((ISLParser) parser).program();
        };
        return errors.count == 0 ? root : null;
    }

    private static SafetyAssessment review(String language, String category, String reason) {
        return new SafetyAssessment(SafetyLevel.REVIEW, category, reason, 0, "ANTLR " + language + " syntax analysis");
    }
    private static SafetyAssessment unknown(String language, String reason) {
        return new SafetyAssessment(SafetyLevel.UNKNOWN, "UNRESOLVED_CONTEXT", reason, 0, "ANTLR " + language + " syntax analysis");
    }
    private static final class Errors extends BaseErrorListener {
        int count;
        @Override public void syntaxError(Recognizer<?, ?> r, Object s, int l, int c, String m, RecognitionException e) { count++; }
    }
}
