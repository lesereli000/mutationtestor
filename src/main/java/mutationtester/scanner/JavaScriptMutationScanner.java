package mutationtester.scanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import mutationtester.JavaScriptLexer;
import mutationtester.JavaScriptParser;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.msl.CstNodes;
import mutationtester.msl.MSLScannerAdapter;

public class JavaScriptMutationScanner {

    // ── MSL adapter ──────────────────────────────────────────────────────────

    public static MSLScannerAdapter createAdapter(File file) throws IOException {
        String sourceCode = Files.readString(file.toPath());

        CharStream input = mutationtester.msl.CstNodes.utf16(sourceCode);
        JavaScriptLexer lexer = new JavaScriptLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaScriptParser parser = new JavaScriptParser(tokens);
        ParseTree tree = parser.program();

        return new MSLScannerAdapter() {

            // JavaScript has no // operator; Math.floor(a / b) is how floor
            // division is written, so it stands in for a // b. Its inner "/" is
            // part of that operator, not a separate division to mutate.

            /** The a / b inside Math.floor(a / b), or null if ctx isn't that idiom. */
            private ParseTree floorDivision(ParseTree ctx) {
                if (!(ctx instanceof JavaScriptParser.ArgumentsExpressionContext call)
                        || !call.singleExpression().getText().equals("Math.floor")
                        || call.arguments().argument().size() != 1) {
                    return null;
                }
                JavaScriptParser.ArgumentContext arg = call.arguments().argument(0);
                if (arg.Ellipsis() != null || arg.singleExpression() == null) return null;
                ParseTree inner = stripParens(arg.singleExpression());
                return inner instanceof JavaScriptParser.MultiplicativeExpressionContext
                        && inner.getChild(1).getText().equals("/") ? inner : null;
            }

            private ParseTree stripParens(ParseTree expr) {
                while (expr instanceof JavaScriptParser.ParenthesizedExpressionContext paren
                        && paren.expressionSequence().singleExpression().size() == 1) {
                    expr = paren.expressionSequence().singleExpression(0);
                }
                return expr;
            }

            private boolean isInsideFloorDivision(ParseTree division) {
                ParseTree p = division.getParent();
                while (p instanceof JavaScriptParser.ParenthesizedExpressionContext
                        || p instanceof JavaScriptParser.ExpressionSequenceContext) {
                    p = p.getParent();
                }
                if (!(p instanceof JavaScriptParser.ArgumentContext) || p.getParent() == null) return false;
                ParseTree call = p.getParent().getParent();
                return call != null && floorDivision(call) == division;
            }

            @Override
            public boolean isBinaryExpressionFor(ParseTree ctx, String mslOperator) {
                if (mslOperator.equals("//")) return floorDivision(ctx) != null;
                return ctx.getChildCount() == 3 && ctx.getChild(1).getText().equals(mslOperator)
                        && !(mslOperator.equals("/") && isInsideFloorDivision(ctx));
            }

            @Override
            public OperatorEdit createOperatorEdit(ParseTree ctx, String fromOperator, String toOperator) {
                // A rule written for === or !== means exactly that operator, so its
                // replacement is written as given: === becomes ==.
                if (fromOperator.equals("===") || fromOperator.equals("!==")) {
                    ParseTree op = getOperatorToken(ctx);
                    int[] pos = getTokenPosition(op);
                    return new OperatorEdit(pos[0], pos[1], getNodeText(op), toOperator);
                }
                ParseTree floor = floorDivision(ctx);
                boolean fromFloor = fromOperator.equals("//") && floor != null;
                if (!fromFloor && !toOperator.equals("//")) {
                    return MSLScannerAdapter.super.createOperatorEdit(ctx, fromOperator, toOperator);
                }
                ParseTree binary = fromFloor ? floor : ctx;
                String left = getNodeText(binary.getChild(0));
                String right = getNodeText(binary.getChild(2));
                String replacement = toOperator.equals("//")
                        ? "Math.floor(" + left + " / " + right + ")"
                        : MSLScannerAdapter.infix(left, toLanguageOperator(toOperator, binary), right);
                int[] pos = getTokenPosition(ctx);
                return new OperatorEdit(pos[0], pos[1], getNodeText(ctx), replacement);
            }
            @Override
            public List<String> getOperatorAliases(String canonicalOperator) {
                // Equality covers both the loose and strict spellings.
                return switch (canonicalOperator) {
                    case "==" -> List.of("==", "===");
                    case "!=" -> List.of("!=", "!==");
                    default -> List.of(canonicalOperator);
                };
            }
            @Override
            public String toLanguageOperator(String canonicalOperator, ParseTree ctx) {
                return switch (canonicalOperator) {
                    // Written as Math.floor(a / b); see createOperatorEdit.
                    case "//" -> null;
                    // Keep strictness: === becomes !== rather than !=.
                    case "==", "!=" -> isStrictEquality(ctx) ? canonicalOperator + "=" : canonicalOperator;
                    default -> canonicalOperator;
                };
            }
            private boolean isStrictEquality(ParseTree ctx) {
                if (ctx == null || ctx.getChildCount() != 3) return false;
                String op = ctx.getChild(1).getText();
                return op.equals("===") || op.equals("!==");
            }
            @Override public ParseTree getLeftOperand(ParseTree ctx) {
                ParseTree floor = floorDivision(ctx);
                return (floor != null ? floor : ctx).getChild(0);
            }
            @Override public ParseTree getRightOperand(ParseTree ctx) {
                ParseTree floor = floorDivision(ctx);
                return (floor != null ? floor : ctx).getChild(2);
            }
            @Override public ParseTree getOperatorToken(ParseTree ctx) {
                return ctx.getChildCount() >= 3 ? ctx.getChild(1) : null;
            }
            @Override
            public boolean isQuantity(ParseTree ctx) {
                if (ctx == null) return false;
                return MSLScannerAdapter.isQuantityText(getNodeText(ctx), ctx instanceof TerminalNode);
            }

            @Override
            public boolean isStringLiteral(ParseTree ctx) {
                return ctx != null && MSLScannerAdapter.isStringLiteral(getNodeText(ctx).strip(), true);
            }
            @Override
            public String getNodeText(ParseTree ctx) {
                if (ctx instanceof ParserRuleContext prc) {
                    Token start = prc.getStart();
                    Token stop = prc.getStop();
                    if (start != null && stop != null
                            && start.getStartIndex() >= 0
                            && stop.getStopIndex() >= start.getStartIndex()
                            && stop.getStopIndex() < sourceCode.length()) {
                        return sourceCode.substring(start.getStartIndex(), stop.getStopIndex() + 1);
                    }
                }
                return ctx.getText();
            }

            // MSL's "s is null" is a null-or-empty check; JavaScript writes it
            // as s == null || s === "" (or s.length === 0).
            @Override
            public ParseTree nullCheckSubject(ParseTree ctx) {
                if (ctx instanceof JavaScriptParser.ArgumentsExpressionContext call
                        && call.singleExpression().getText().equals("string.IsNullOrEmpty")) {
                    List<JavaScriptParser.ArgumentContext> args = call.arguments().argument();
                    return args.size() == 1 && args.get(0).singleExpression() != null
                            ? args.get(0).singleExpression() : null;
                }
                if (!(ctx instanceof JavaScriptParser.LogicalOrExpressionContext or)) return null;
                ParseTree left = or.singleExpression(0);
                ParseTree right = or.singleExpression(1);
                if (!isEquality(left) || !CstNodes.isDottedName(left.getChild(0))) return null;
                String nothing = left.getChild(2).getText();
                if (!nothing.equals("null") && !nothing.equals("undefined")) return null;
                ParseTree subject = left.getChild(0);
                String s = subject.getText();
                if (!isEquality(right)) return null;
                String lhs = right.getChild(0).getText();
                String rhs = right.getChild(2).getText();
                boolean emptyString = lhs.equals(s) && (rhs.equals("\"\"") || rhs.equals("''"));
                boolean zeroLength = lhs.equals(s + ".length") && rhs.equals("0");
                return emptyString || zeroLength ? subject : null;
            }

            @Override
            public List<ParseTree> callArguments(ParseTree ctx, String functionName) {
                if (!(ctx instanceof JavaScriptParser.ArgumentsExpressionContext call)
                        || !call.singleExpression().getText().equals(functionName)) {
                    return null;
                }
                List<ParseTree> args = new ArrayList<>();
                for (JavaScriptParser.ArgumentContext arg : call.arguments().argument()) {
                    if (arg.Ellipsis() != null || arg.singleExpression() == null) return null;
                    args.add(arg.singleExpression());
                }
                return args;
            }

            /** a == b or a === b. */
            private boolean isEquality(ParseTree node) {
                return node instanceof JavaScriptParser.EqualityExpressionContext
                        && (node.getChild(1).getText().equals("==") || node.getChild(1).getText().equals("==="));
            }

            @Override
            public String notEmptyStringCheck(String subject) {
                return "(" + subject + " !== \"\")";
            }

            @Override
            public boolean isInvocationStatement(ParseTree ctx) {
                if (ctx instanceof ParserRuleContext prc) {
                    Token start = prc.getStart();
                    Token stop = prc.getStop();
                    if (start != null && stop != null && start.getLine() != stop.getLine()) {
                        return false;
                    }
                }
                // A one-line piece of code that starts like a call, name(.name|[i])*( ,
                // and ends with ");" - a call statement, or a statement around one.
                List<Token> t = CstNodes.tokens(ctx);
                if (t.size() < 4 || t.get(t.size() - 1).getType() != JavaScriptLexer.SemiColon
                        || t.get(t.size() - 2).getType() != JavaScriptLexer.CloseParen) {
                    return false;
                }
                String first = t.get(0).getText();
                if (first.startsWith("return") || first.startsWith("throw")
                        || first.isEmpty() || (!Character.isLetter(first.charAt(0)) && first.charAt(0) != '_'
                            && first.charAt(0) != '$')) {
                    return false;
                }
                for (Token token : t) {
                    if (token.getType() == JavaScriptLexer.OpenParen) return true;
                    String text = token.getText();
                    boolean nameLike = !text.isEmpty();
                    for (int i = 0; i < text.length(); i++) {
                        char c = text.charAt(i);
                        if (!Character.isLetterOrDigit(c) && c != '_' && c != '$' && c != '.' && c != '[' && c != ']') {
                            nameLike = false;
                        }
                    }
                    if (!nameLike) return false;
                }
                return false;
            }

            // ── Statements, for Except: trees ────────────────────────────

            @Override
            public List<ParseTree> matchNode(mutationtester.msl.MSLNode pattern, ParseTree ctx) {
                switch (pattern.kind()) {
                    case mutationtester.msl.MSLNode.IF -> {
                        return matchIf(pattern, ctx, false);
                    }
                    case mutationtester.msl.MSLNode.WHILE -> {
                        ParseTree s = statementBody(ctx);
                        return s instanceof JavaScriptParser.WhileStatementContext w
                                ? List.of(w.expressionSequence(), w.statement()) : null;
                    }
                    case mutationtester.msl.MSLNode.FOR -> {
                        ParseTree s = statementBody(ctx);
                        if (!(s instanceof JavaScriptParser.ForStatementContext f)) return null;
                        // for (init; condition; update): place each part by the ';' around it.
                        ParseTree[] parts = new ParseTree[3];
                        int part = 0;
                        for (int i = 2; i < f.getChildCount() && part < 3; i++) {
                            ParseTree child = f.getChild(i);
                            String text = child.getText();
                            if (child instanceof TerminalNode && (text.equals(";") || text.equals(")"))) {
                                part++;
                            } else if (!(child instanceof TerminalNode)) {
                                parts[part] = child;
                            }
                        }
                        return java.util.Arrays.asList(parts[0], parts[1], parts[2], f.statement());
                    }
                    case mutationtester.msl.MSLNode.ASSIGN -> {
                        ParseTree s = statementBody(ctx);
                        ParseTree expr = s instanceof JavaScriptParser.ExpressionStatementContext e
                                && e.expressionSequence().singleExpression().size() == 1
                                ? e.expressionSequence().singleExpression(0) : ctx;
                        return expr instanceof JavaScriptParser.AssignmentExpressionContext a
                                ? List.of(a.singleExpression(0), a.singleExpression(1)) : null;
                    }
                    case mutationtester.msl.MSLNode.BLOCK -> {
                        ParseTree s = statementBody(ctx);
                        if (!(s instanceof JavaScriptParser.BlockContext b) || b.statementList() == null
                                || b.statementList().statement().size() < pattern.children().size()) {
                            return null;
                        }
                        return new ArrayList<>(b.statementList().statement().subList(0, pattern.children().size()));
                    }
                    case mutationtester.msl.MSLNode.UNARY -> {
                        boolean fits = switch (pattern.value()) {
                            case "!" -> ctx instanceof JavaScriptParser.NotExpressionContext;
                            case "-" -> ctx instanceof JavaScriptParser.UnaryMinusExpressionContext;
                            case "~" -> ctx instanceof JavaScriptParser.BitNotExpressionContext;
                            default -> false;
                        };
                        return fits ? List.of(((JavaScriptParser.SingleExpressionContext) ctx).getChild(1)) : null;
                    }
                    case mutationtester.msl.MSLNode.CONDITIONAL -> {
                        return ctx instanceof JavaScriptParser.TernaryExpressionContext t
                                ? List.of(t.singleExpression(0), t.singleExpression(1), t.singleExpression(2)) : null;
                    }
                    case mutationtester.msl.MSLNode.COMPOUND_ASSIGN -> {
                        return ctx instanceof JavaScriptParser.AssignmentOperatorExpressionContext a
                                && a.assignmentOperator().getText().equals(pattern.value())
                                ? List.of(a.singleExpression(0), a.singleExpression(1)) : null;
                    }
                    case mutationtester.msl.MSLNode.INDEX -> {
                        return ctx instanceof JavaScriptParser.MemberIndexExpressionContext m
                                && !m.getText().contains("?.[") && m.expressionSequence().singleExpression().size() == 1
                                ? List.of(m.singleExpression(), m.expressionSequence().singleExpression(0)) : null;
                    }
                    case mutationtester.msl.MSLNode.MEMBER -> {
                        // a.b, but not the a.b of a call a.b(c), which is a method call
                        return ctx instanceof JavaScriptParser.MemberDotExpressionContext m && isPlainDot(m)
                                && !(m.getParent() instanceof JavaScriptParser.ArgumentsExpressionContext call
                                    && call.singleExpression() == m)
                                ? List.of(m.singleExpression(), m.identifierName()) : null;
                    }
                    case mutationtester.msl.MSLNode.MEMBER_CALL -> {
                        if (!(ctx instanceof JavaScriptParser.ArgumentsExpressionContext call)
                                || !(call.singleExpression() instanceof JavaScriptParser.MemberDotExpressionContext m)
                                || !isPlainDot(m)) {
                            return null;
                        }
                        List<ParseTree> parts = new ArrayList<>(List.of(m.singleExpression(), m.identifierName()));
                        parts.addAll(call.arguments().argument());
                        return parts;
                    }
                    case mutationtester.msl.MSLNode.RETURN -> {
                        if (!(statementBody(ctx) instanceof JavaScriptParser.ReturnStatementContext r)) return null;
                        return r.expressionSequence() == null ? List.of() : List.of(r.expressionSequence());
                    }
                    case mutationtester.msl.MSLNode.BREAK -> {
                        if (!(statementBody(ctx) instanceof JavaScriptParser.BreakStatementContext b)) return null;
                        return b.identifier() == null ? List.of() : List.of(b.identifier());
                    }
                    case mutationtester.msl.MSLNode.CONTINUE -> {
                        if (!(statementBody(ctx) instanceof JavaScriptParser.ContinueStatementContext c)) return null;
                        return c.identifier() == null ? List.of() : List.of(c.identifier());
                    }
                    case mutationtester.msl.MSLNode.THROW -> {
                        return statementBody(ctx) instanceof JavaScriptParser.ThrowStatementContext t
                                ? List.of(t.expressionSequence()) : null;
                    }
                    case mutationtester.msl.MSLNode.DO_WHILE -> {
                        return statementBody(ctx) instanceof JavaScriptParser.DoStatementContext d
                                ? List.of(d.statement(), d.expressionSequence()) : null;
                    }
                    case mutationtester.msl.MSLNode.FOR_EACH -> {
                        ParseTree s = statementBody(ctx);
                        if (s instanceof JavaScriptParser.ForOfStatementContext f) {
                            return List.of(loopVariable(f.singleExpression(), f.singleVariableDeclaration()),
                                    f.expressionSequence(), f.statement());
                        }
                        if (s instanceof JavaScriptParser.ForInStatementContext f) {
                            return List.of(loopVariable(f.singleExpression(), f.singleVariableDeclaration()),
                                    f.expressionSequence(), f.statement());
                        }
                        return null;
                    }
                    case mutationtester.msl.MSLNode.TRY -> {
                        if (!(statementBody(ctx) instanceof JavaScriptParser.TryStatementContext t)) return null;
                        return java.util.Arrays.asList(t.block(),
                                t.catchProduction() == null ? null : t.catchProduction().block(),
                                t.finallyProduction() == null ? null : t.finallyProduction().block());
                    }
                    case mutationtester.msl.MSLNode.CASE -> {
                        return ctx instanceof JavaScriptParser.CaseClauseContext c && c.statementList() != null
                                ? List.of(c.expressionSequence(), c.statementList()) : null;
                    }
                    case mutationtester.msl.MSLNode.DEFAULT_CASE -> {
                        return ctx instanceof JavaScriptParser.DefaultClauseContext c && c.statementList() != null
                                ? List.of(c.statementList()) : null;
                    }
                    case mutationtester.msl.MSLNode.DECLARE -> {
                        if (!(ctx instanceof JavaScriptParser.VariableDeclarationListContext list)
                                || list.variableDeclaration().size() != 1
                                || list.variableDeclaration(0).singleExpression() == null) {
                            return null;
                        }
                        JavaScriptParser.VariableDeclarationContext d = list.variableDeclaration(0);
                        return List.of(d.assignable(), d.singleExpression());
                    }
                    case mutationtester.msl.MSLNode.COLLECTION -> {
                        return ctx instanceof JavaScriptParser.ArrayLiteralExpressionContext a
                                ? new ArrayList<>(a.arrayLiteral().elementList().arrayElement()) : null;
                    }
                    case mutationtester.msl.MSLNode.OBJECT -> {
                        return ctx instanceof JavaScriptParser.ObjectLiteralExpressionContext o
                                ? new ArrayList<>(o.objectLiteral().propertyAssignment()) : null;
                    }
                    case mutationtester.msl.MSLNode.ENTRY -> {
                        return ctx instanceof JavaScriptParser.PropertyExpressionAssignmentContext p
                                ? List.of(p.propertyName(), p.singleExpression()) : null;
                    }
                    case mutationtester.msl.MSLNode.LAMBDA -> {
                        if (!(ctx instanceof JavaScriptParser.ArrowFunctionContext a)) return null;
                        JavaScriptParser.ArrowFunctionParametersContext p = a.arrowFunctionParameters();
                        List<ParseTree> parts = new ArrayList<>();
                        if (p.propertyName() != null) parts.add(p.propertyName());
                        else if (p.formalParameterList() != null) parts.addAll(p.formalParameterList().formalParameterArg());
                        parts.add(a.arrowFunctionBody());
                        return parts;
                    }
                    case mutationtester.msl.MSLNode.FUNCTION -> {
                        if (!(ctx instanceof JavaScriptParser.FunctionDeclarationContext f)) return null;
                        List<ParseTree> parts = new ArrayList<>(List.of(f.identifier()));
                        if (f.formalParameterList() != null) parts.addAll(f.formalParameterList().formalParameterArg());
                        parts.add(f.functionBody());
                        return parts;
                    }
                    case mutationtester.msl.MSLNode.PARAMETER -> {
                        if (!(ctx instanceof JavaScriptParser.FormalParameterArgContext p)) return null;
                        return p.singleExpression() == null ? List.of(p.assignable()) : List.of(p.assignable(), p.singleExpression());
                    }
                    case mutationtester.msl.MSLNode.TYPE_CHECK -> {
                        return ctx instanceof JavaScriptParser.InstanceofExpressionContext i
                                ? List.of(i.singleExpression(0), i.singleExpression(1)) : null;
                    }
                    case mutationtester.msl.MSLNode.SAFE_MEMBER -> {
                        if (ctx instanceof JavaScriptParser.MemberDotExpressionContext m && !isPlainDot(m)
                                && m.getText().contains("?.")) {
                            return List.of(m.singleExpression(), m.identifierName());
                        }
                        return ctx instanceof JavaScriptParser.OptionalChainExpressionContext o
                                ? List.of(o.singleExpression(0), o.singleExpression(1)) : null;
                    }
                    case mutationtester.msl.MSLNode.FIELD -> {
                        if (!(ctx instanceof JavaScriptParser.FieldDefinitionContext f)) return null;
                        return f.initializer() == null ? List.of(f.classElementName())
                                : List.of(f.classElementName(), f.initializer().singleExpression());
                    }
                    case mutationtester.msl.MSLNode.YIELD -> {
                        ParseTree s = ctx instanceof JavaScriptParser.YieldExpressionContext y ? y.yieldStatement() : statementBody(ctx);
                        if (!(s instanceof JavaScriptParser.YieldStatementContext y)) return null;
                        return y.expressionSequence() == null ? List.of() : List.of(y.expressionSequence());
                    }
                    case mutationtester.msl.MSLNode.LABELED -> {
                        return statementBody(ctx) instanceof JavaScriptParser.LabelledStatementContext l
                                ? List.of(l.identifier(), l.statement()) : null;
                    }
                    case mutationtester.msl.MSLNode.MEMBERSHIP -> {
                        return pattern.value().equals("in") && ctx instanceof JavaScriptParser.InExpressionContext i
                                ? List.of(i.singleExpression(0), i.singleExpression(1)) : null;
                    }
                    case mutationtester.msl.MSLNode.TUPLE -> {
                        // [a, b] as destructuring targets or an array of values
                        return ctx instanceof JavaScriptParser.ArrayLiteralExpressionContext a
                                ? new ArrayList<>(a.arrayLiteral().elementList().arrayElement()) : null;
                    }
                    case mutationtester.msl.MSLNode.SPREAD -> {
                        if (ctx instanceof JavaScriptParser.ArrayElementContext e && e.Ellipsis() != null) return List.of(e.singleExpression());
                        if (ctx instanceof JavaScriptParser.ArgumentContext a && a.Ellipsis() != null) return List.of(a.getChild(1));
                        return null;
                    }
                    case mutationtester.msl.MSLNode.DELETE -> {
                        return ctx instanceof JavaScriptParser.DeleteExpressionContext d ? List.of(d.singleExpression()) : null;
                    }
                    case mutationtester.msl.MSLNode.INTERPOLATION -> {
                        return ctx instanceof JavaScriptParser.TemplateStringLiteralContext t ? new ArrayList<>(templateHoles(t)) : null;
                    }
                    case mutationtester.msl.MSLNode.AWAIT -> {
                        return ctx instanceof JavaScriptParser.AwaitExpressionContext a ? List.of(a.singleExpression()) : null;
                    }
                    default -> {
                        return MSLScannerAdapter.super.matchNode(pattern, ctx);
                    }
                }
            }

            /** The specific statement inside a statement wrapper (statement: ifStatement | block | ...). */
            private ParseTree statementBody(ParseTree ctx) {
                ParseTree p = ctx;
                while ((p instanceof JavaScriptParser.StatementContext || p instanceof JavaScriptParser.IterationStatementContext)
                        && p.getChildCount() == 1) {
                    p = p.getChild(0);
                }
                return p;
            }

            /** a.b, not a?.b or a.#b. */
            private boolean isPlainDot(JavaScriptParser.MemberDotExpressionContext m) {
                for (int i = 0; i < m.getChildCount(); i++) {
                    String t = m.getChild(i).getText();
                    if (m.getChild(i) instanceof TerminalNode && (t.equals("?") || t.equals("#"))) return false;
                }
                return true;
            }

            /** f(a, b): [f, a, b]. */
            @Override
            public List<ParseTree> callParts(ParseTree ctx) {
                if (!(ctx instanceof JavaScriptParser.ArgumentsExpressionContext call)) return null;
                List<ParseTree> parts = new ArrayList<>(List.of(call.singleExpression()));
                parts.addAll(call.arguments().argument());
                return parts;
            }

            // ── Literals and type names ──────────────────────────────────────

            @Override
            public mutationtester.msl.ValueKind valueKind(ParseTree ctx) {
                return Literals.valueKind(Literals.Lang.JAVASCRIPT, ctx);
            }

            @Override
            public String convert(ParseTree node, String fromType, String toType, String toCondition, ParseTree ctx) {
                mutationtester.msl.ValueKind from = valueKind(node);
                if (from == null) return null;
                return Literals.convert(Literals.Lang.JAVASCRIPT, getNodeText(node).strip(), from, toType, toCondition);
            }

            // ── Statements ────────────────────────────────────────────────

            @Override
            public List<ParseTree> ifParts(ParseTree ctx) {
                if (!(statementBody(ctx) instanceof JavaScriptParser.IfStatementContext i)) return null;
                return java.util.Arrays.asList(i.expressionSequence(), i.statement(0),
                        i.statement().size() > 1 ? i.statement(1) : null);
            }

            /** x in for (const x of xs): the name, not "const x". */
            private ParseTree loopVariable(ParseTree expression, JavaScriptParser.SingleVariableDeclarationContext declaration) {
                return declaration != null ? declaration.variableDeclaration().assignable() : expression;
            }

            @Override
            public boolean isBlock(ParseTree ctx) {
                return statementBody(ctx) instanceof JavaScriptParser.BlockContext
                        || ctx instanceof JavaScriptParser.FunctionBodyContext;
            }

            /** Keeps the declaring keyword: const n = 0 stays const. */
            @Override
            public String renderDeclaration(String name, String value, ParseTree ctx) {
                String keyword = ctx instanceof JavaScriptParser.VariableDeclarationListContext list
                        ? list.varModifier().getText() : "let";
                return keyword + " " + name + " = " + value;
            }

            @Override
            public String renderForEach(String name, String collection, String body, ParseTree ctx) {
                return "for (const " + name + " of " + collection + ") " + body;
            }

            // ── Collections, functions and types ────────────────────────────

            @Override
            public boolean isLambda(ParseTree ctx) {
                return ctx instanceof JavaScriptParser.ArrowFunctionContext
                        || ctx instanceof JavaScriptParser.AnonymousFunctionDeclContext;
            }

            /** The right side of instanceof: the class it tests for. */
            @Override
            public boolean isTypeName(ParseTree ctx) {
                return ctx.getParent() instanceof JavaScriptParser.InstanceofExpressionContext i && i.singleExpression(1) == ctx;
            }

            @Override
            public boolean isField(ParseTree ctx) {
                return ctx instanceof JavaScriptParser.FieldDefinitionContext;
            }

            @Override
            public String emptyCollection(ParseTree ctx) {
                return ctx instanceof JavaScriptParser.ObjectLiteralExpressionContext ? "{}" : "[]";
            }

            @Override
            public String renderSafeMember(String object, String member, ParseTree ctx) {
                return object + "?." + member;
            }

            // ── Remaining statements ────────────────────────────────────────

            private List<ParseTree> templateHoles(JavaScriptParser.TemplateStringLiteralContext t) {
                List<ParseTree> holes = new ArrayList<>();
                for (JavaScriptParser.TemplateStringAtomContext atom : t.templateStringAtom()) {
                    if (atom.singleExpression() != null) holes.add(atom.singleExpression());
                }
                return holes;
            }

            @Override
            public String renderKeywordStatement(String kind, String value, ParseTree ctx) {
                return kind.equals("delete") ? "delete " + value : null;
            }

            @Override
            public String renderMembership(String operator, String item, String container, ParseTree ctx) {
                return operator.equals("in") ? item + " in " + container : "!(" + item + " in " + container + ")";
            }

            @Override
            public String renderTuple(List<String> elements, ParseTree ctx) {
                return "[" + String.join(", ", elements) + "]";
            }

            @Override
            public String renderSpread(String value, ParseTree ctx) {
                return "..." + value;
            }

            @Override
            public String renderInterpolation(List<String> holes, ParseTree ctx) {
                if (!(ctx instanceof JavaScriptParser.TemplateStringLiteralContext t)) return null;
                return mutationtester.msl.CstNodes.replaceHoles(t, templateHoles(t), holes, getNodeText(t));
            }

            // ── Increments ───────────────────────────────────────────────

            @Override
            public ParseTree incrementOperand(String operator, ParseTree ctx) {
                if (operator.equals("++")) {
                    if (ctx instanceof JavaScriptParser.PostIncrementExpressionContext e) return e.singleExpression();
                    if (ctx instanceof JavaScriptParser.PreIncrementExpressionContext e) return e.singleExpression();
                } else if (operator.equals("--")) {
                    if (ctx instanceof JavaScriptParser.PostDecreaseExpressionContext e) return e.singleExpression();
                    if (ctx instanceof JavaScriptParser.PreDecreaseExpressionContext e) return e.singleExpression();
                }
                return null;
            }

            @Override
            public ParseTree expressionStatement(ParseTree ctx) {
                return ctx.getParent() instanceof JavaScriptParser.ExpressionSequenceContext seq
                        && seq.singleExpression().size() == 1
                        && seq.getParent() instanceof JavaScriptParser.ExpressionStatementContext statement
                        ? statement : null;
            }

            @Override
            public boolean discardsValue(ParseTree ctx) {
                if (expressionStatement(ctx) != null) return true;
                // for (...; ...; i++): the update follows the header's second ';'
                if (!(ctx.getParent() instanceof JavaScriptParser.ExpressionSequenceContext seq)
                        || !(seq.getParent() instanceof JavaScriptParser.ForStatementContext f)) {
                    return false;
                }
                int semicolons = 0;
                for (int i = 0; i < f.getChildCount() && f.getChild(i) != seq; i++) {
                    if (f.getChild(i) instanceof TerminalNode t && t.getText().equals(";")) semicolons++;
                }
                return semicolons == 2;
            }

            @Override
            public java.util.Set<String> supportedKinds() {
                return java.util.Set.of("binary", "predicate", "literal", "identifier", "call",
                        "if", "while", "for", "assign", "block", "increment", "return", "break", "continue", "declare", "for_each", "do_while", "case", "default_case", "throw", "try",
                        "unary", "conditional", "compound_assign", "index", "member", "member_call",
                        "collection", "object", "entry", "lambda", "function", "parameter", "type_check", "safe_member",
                        "field", "yield", "labeled", "membership", "tuple", "spread", "delete", "interpolation", "await");
            }

            @Override
            public java.util.Set<String> supportedPlaceholders() {
                return java.util.Set.of("quantity", "match", "statement", "invocation_statement",
                        "return_value", "constructor_call", "non_void_method_call",
                        "equality_condition", "order_condition",
                        "number", "integer", "float", "exponential", "based_integer", "string", "boolean",
                        "condition", "name", "arguments", "block", "contains", "collection", "lambda", "type", "field");
            }

            // ── PIT-style placeholders ───────────────────────────────────

            @Override
            public boolean matchesPlaceholder(String type, ParseTree ctx) {
                return switch (type) {
                    case "statement" -> ctx instanceof JavaScriptParser.StatementContext
                            || ctx instanceof JavaScriptParser.StatementListContext;
                    case "return_value" -> ctx instanceof JavaScriptParser.ExpressionSequenceContext
                            && ctx.getParent() instanceof JavaScriptParser.ReturnStatementContext
                            && renderValue("empty_value", ctx) != null;
                    case "constructor_call" -> isConstructorCall(ctx);
                    case "non_void_method_call" -> isReturnedCall(ctx) || isAssignedCall(ctx);
                    case "equality_condition" -> isCondition(ctx) && CstNodes.anyTokenContains(ctx, "==", "!=");
                    case "order_condition" -> isCondition(ctx) && CstNodes.anyTokenContains(ctx, "<", ">");
                    default -> MSLScannerAdapter.super.matchesPlaceholder(type, ctx);
                };
            }

            private boolean singleLine(ParseTree ctx) {
                Token start = CstNodes.start(ctx);
                Token stop = CstNodes.stop(ctx);
                return start != null && stop != null && start.getLine() == stop.getLine();
            }

            /**
             * new T(...) on one line. Inside another constructor call only when
             * that outer call spans several lines (and so isn't replaced itself).
             */
            private boolean isConstructorCall(ParseTree ctx) {
                if (!(ctx instanceof JavaScriptParser.NewExpressionContext) || !singleLine(ctx)) return false;
                JavaScriptParser.NewExpressionContext outer = null;
                for (ParseTree p = ctx.getParent(); p != null; p = p.getParent()) {
                    if (p instanceof JavaScriptParser.NewExpressionContext n) outer = n;
                }
                return outer == null || !singleLine(outer);
            }

            /** return f(...) on one line, where the call is the whole value. */
            private boolean isReturnedCall(ParseTree ctx) {
                if (!(ctx instanceof JavaScriptParser.ArgumentsExpressionContext) || !singleLine(ctx)
                        || !(ctx.getParent() instanceof JavaScriptParser.ExpressionSequenceContext seq)
                        || seq.singleExpression().size() != 1
                        || !(seq.getParent() instanceof JavaScriptParser.ReturnStatementContext ret)) {
                    return false;
                }
                // A call inside an already-replaced returned call is left alone.
                for (ParseTree p = ret.getParent(); p != null; p = p.getParent()) {
                    if (p instanceof JavaScriptParser.ArgumentsExpressionContext outer && isReturnedCall(outer)) return false;
                }
                return true;
            }

            /** name(...) or a.b.name(...) right after an '=' on its line, closed on that line. */
            private boolean isAssignedCall(ParseTree ctx) {
                if (!(ctx instanceof JavaScriptParser.ArgumentsExpressionContext call) || !singleLine(ctx)
                        || !CstNodes.isDottedName(call.singleExpression())) {
                    return false;
                }
                Token first = call.getStart();
                char initial = first.getText().charAt(0);
                if (initial == '$' || CALL_LIKE_KEYWORDS.contains(first.getText())) return false;
                Token previous = null;
                for (int i = first.getTokenIndex() - 1; i >= 0 && previous == null; i--) {
                    if (tokens.get(i).getChannel() == Token.DEFAULT_CHANNEL) previous = tokens.get(i);
                }
                return previous != null && previous.getType() == JavaScriptLexer.Assign
                        && previous.getLine() == first.getLine();
            }

            private static final java.util.Set<String> CALL_LIKE_KEYWORDS = java.util.Set.of(
                    "function", "switch", "if", "while", "for", "typeof", "await", "yield");

            /** The condition of an if, while or do-while. */
            private boolean isCondition(ParseTree ctx) {
                ParseTree parent = ctx.getParent();
                return ctx instanceof JavaScriptParser.ExpressionSequenceContext
                        && (parent instanceof JavaScriptParser.IfStatementContext
                            || parent instanceof JavaScriptParser.WhileStatementContext
                            || parent instanceof JavaScriptParser.DoStatementContext);
            }

            @Override
            public String renderValue(String placeholderType, ParseTree ctx) {
                switch (placeholderType) {
                    case "empty_value" -> {
                        // A returned string, array or object literal becomes empty;
                        // another one-line "return x;" gets a value judged from x.
                        String compact = ctx.getText();
                        String empty = emptyLiteral(compact);
                        if (empty != null && !empty.isEmpty()) return empty;
                        Token next = null;
                        for (int i = ((ParserRuleContext) ctx).getStop().getTokenIndex() + 1; i < tokens.size() && next == null; i++) {
                            if (tokens.get(i).getChannel() == Token.DEFAULT_CHANNEL) next = tokens.get(i);
                        }
                        Token start = ((ParserRuleContext) ctx).getStart();
                        Token returnToken = ((ParserRuleContext) ctx.getParent()).getStart();
                        if (next == null || next.getType() != JavaScriptLexer.SemiColon || !singleLine(ctx)
                                || next.getLine() != start.getLine() || returnToken.getLine() != start.getLine()) {
                            return null;
                        }
                        return TypedEmptyValues.forExpression(getNodeText(ctx), "null", "false");
                    }
                    case "default_value" -> {
                        return "null";
                    }
                    default -> {
                        return null;
                    }
                }
            }

            /** The empty form of a non-empty string, array or object literal; "" when it is already empty; null otherwise. */
            private String emptyLiteral(String value) {
                if (isDelimited(value, '"', '"') || isDelimited(value, '\'', '\'') || isDelimited(value, '`', '`')) {
                    return value.length() == 2 ? "" : "\"\"";
                }
                if (isDelimited(value, '[', ']')) return value.equals("[]") ? "" : "[]";
                if (isDelimited(value, '{', '}')) return value.equals("{}") ? "" : "{}";
                return null;
            }

            private boolean isDelimited(String value, char open, char close) {
                return !value.isEmpty() && value.charAt(0) == open && value.charAt(value.length() - 1) == close;
            }

            @Override
            public int[] getTokenPosition(ParseTree ctx) {
                if (ctx instanceof TerminalNode tn) {
                    Token s = tn.getSymbol();
                    return new int[]{s.getLine(), s.getCharPositionInLine()};
                }
                if (ctx instanceof ParserRuleContext prc) {
                    Token s = prc.getStart();
                    return new int[]{s.getLine(), s.getCharPositionInLine()};
                }
                return new int[]{0, 0};
            }
            @Override public ParseTree getRootContext() { return tree; }
        };
    }
}
