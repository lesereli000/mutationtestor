package mutationtester.scanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.antlr.v4.gui.TreeViewer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import mutationtester.JavaLexer;
import mutationtester.JavaParser;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.msl.MSLScannerAdapter;

public class JavaMutationScanner {
    public static MSLScannerAdapter createAdapter(File file) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        CharStream input = mutationtester.msl.CstNodes.utf16(sourceCode);
        JavaLexer lexer = new JavaLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);
        ParseTree tree = parser.compilationUnit();

        return new MSLScannerAdapter() {

            // Java has no // or ** operator. The library calls that compute the
            // same values stand in for them: Math.floorDiv(a, b) is a // b and
            // Math.pow(a, b) is a ** b.
            private static final java.util.Map<String, String> CALL_OPERATORS =
                    java.util.Map.of("//", "floorDiv", "**", "pow");
            private static final java.util.Set<String> MATH_CLASSES =
                    java.util.Set.of("Math", "StrictMath", "java.lang.Math", "java.lang.StrictMath");

            /** The two arguments of Math.floorDiv / Math.pow when ctx spells the operator, else null. */
            private List<JavaParser.ExpressionContext> callOperands(ParseTree ctx, String mslOperator) {
                String method = CALL_OPERATORS.get(mslOperator);
                if (method == null
                        || !(ctx instanceof JavaParser.MemberReferenceExpressionContext member)
                        || member.methodCall() == null
                        || member.methodCall().identifier() == null
                        || !member.methodCall().identifier().getText().equals(method)
                        || !MATH_CLASSES.contains(member.expression().getText())
                        || member.methodCall().arguments().expressionList() == null) {
                    return null;
                }
                List<JavaParser.ExpressionContext> args = member.methodCall().arguments().expressionList().expression();
                return args.size() == 2 ? args : null;
            }

            private List<JavaParser.ExpressionContext> anyCallOperands(ParseTree ctx) {
                for (String op : CALL_OPERATORS.keySet()) {
                    List<JavaParser.ExpressionContext> args = callOperands(ctx, op);
                    if (args != null) return args;
                }
                return null;
            }

            // Java compares values with a.equals(b) / Objects.equals(a, b), the
            // counterpart of == in C# and Python and equal? in Racket, and
            // !a.equals(b) is the matching !=.

            /** [a, b] for a.equals(b) or Objects.equals(a, b), else null. */
            private List<ParseTree> equalsOperands(ParseTree ctx) {
                if (!(ctx instanceof JavaParser.MemberReferenceExpressionContext member)
                        || member.methodCall() == null
                        || member.methodCall().identifier() == null
                        || !member.methodCall().identifier().getText().equals("equals")
                        || member.methodCall().arguments().expressionList() == null) {
                    return null;
                }
                List<JavaParser.ExpressionContext> args = member.methodCall().arguments().expressionList().expression();
                String target = member.expression().getText();
                if (args.size() == 2 && (target.equals("Objects") || target.equals("java.util.Objects"))) {
                    return List.of(args.get(0), args.get(1));
                }
                return args.size() == 1 ? List.of(member.expression(), args.get(0)) : null;
            }

            /** The a.equals(b) inside !a.equals(b), else null. */
            private ParseTree negatedEquals(ParseTree ctx) {
                if (!(ctx instanceof JavaParser.UnaryOperatorExpressionContext unary)
                        || unary.prefix == null || !unary.prefix.getText().equals("!")) {
                    return null;
                }
                ParseTree inner = unary.expression();
                while (inner.getChildCount() == 1
                        || (inner.getChildCount() == 3 && inner.getChild(0).getText().equals("(")
                            && inner.getChild(2).getText().equals(")"))) {
                    inner = inner.getChildCount() == 1 ? inner.getChild(0) : inner.getChild(1);
                }
                return equalsOperands(inner) != null ? inner : null;
            }

            /** Operands of an equals call or its negation, else null. */
            private List<ParseTree> anyEqualsOperands(ParseTree ctx) {
                List<ParseTree> operands = equalsOperands(ctx);
                ParseTree inner = operands == null ? negatedEquals(ctx) : null;
                return inner != null ? equalsOperands(inner) : operands;
            }

            @Override
            public boolean isBinaryExpressionFor(ParseTree ctx, String mslOperator) {
                if (CALL_OPERATORS.containsKey(mslOperator)) return callOperands(ctx, mslOperator) != null;
                if (mslOperator.equals("==") && equalsOperands(ctx) != null) return true;
                if (mslOperator.equals("!=") && negatedEquals(ctx) != null) return true;
                if (!(ctx instanceof JavaParser.BinaryOperatorExpressionContext binCtx)) return false;
                
                // Case 1: Operator is a single token (bop is set)
                if (binCtx.bop != null) {
                    return binCtx.bop.getText().equals(mslOperator);
                }
                
                // Case 2: Operator is multiple tokens (e.g., >>, >>>, <<)
                // We reconstruct the operator text from the middle children
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < binCtx.getChildCount() - 1; i++) {
                    sb.append(binCtx.getChild(i).getText());
                }
                return sb.toString().equals(mslOperator);
            }

            @Override
            public String toLanguageOperator(String canonicalOperator, ParseTree ctx) {
                // // and ** exist only as library calls (see CALL_OPERATORS).
                return switch (canonicalOperator) {
                    case "//", "**" -> null;
                    default -> canonicalOperator;
                };
            }

            @Override
            public OperatorEdit createOperatorEdit(ParseTree ctx, String fromOperator, String toOperator) {
                List<ParseTree> equals = ("==".equals(fromOperator) || "!=".equals(fromOperator))
                        && !(ctx instanceof JavaParser.BinaryOperatorExpressionContext) ? anyEqualsOperands(ctx) : null;
                if (equals != null) {
                    // a.equals(b) <-> !a.equals(b), like Racket's (= a b) <-> (not (= a b)).
                    int[] pos = getTokenPosition(ctx);
                    String text = getNodeText(ctx);
                    String replacement;
                    if ("==".equals(fromOperator) && "!=".equals(toOperator)) {
                        replacement = "!" + text;
                    } else if ("!=".equals(fromOperator) && "==".equals(toOperator)) {
                        replacement = getNodeText(negatedEquals(ctx));
                    } else {
                        String op = toLanguageOperator(toOperator, ctx);
                        if (op == null) return null;
                        replacement = MSLScannerAdapter.infix(getNodeText(equals.get(0)), op, getNodeText(equals.get(1)));
                    }
                    return new OperatorEdit(pos[0], pos[1], text, replacement);
                }
                List<JavaParser.ExpressionContext> call = callOperands(ctx, fromOperator);
                if (call != null) {
                    // Math.floorDiv(a, b) -> (a / b). Turning one library call into
                    // the other isn't type-safe (floorDiv takes integers, pow returns
                    // double), so only plain operators are targets.
                    String op = toLanguageOperator(toOperator, ctx);
                    if (op == null) return null;
                    int[] pos = getTokenPosition(ctx);
                    return new OperatorEdit(pos[0], pos[1], getNodeText(ctx),
                            MSLScannerAdapter.infix(getNodeText(call.get(0)), op, getNodeText(call.get(1))));
                }
                // An operator can't become Math.floorDiv/Math.pow either: the
                // operands may be doubles (floorDiv) or the result an int (pow).
                String replacement = toLanguageOperator(toOperator, ctx);
                if (replacement == null
                        || !(ctx instanceof JavaParser.BinaryOperatorExpressionContext binCtx)
                        || !(binCtx.getChild(1) instanceof TerminalNode firstOp)
                        || !(binCtx.getChild(binCtx.getChildCount() - 2) instanceof TerminalNode lastOp)) {
                    return null;
                }
                // Shift operators are lexed as separate tokens ('>' '>'), so the
                // edit spans every token between the two operands.
                Token first = firstOp.getSymbol();
                Token last = lastOp.getSymbol();
                String original = sourceCode.substring(first.getStartIndex(), last.getStopIndex() + 1);
                return new OperatorEdit(first.getLine(), first.getCharPositionInLine(), original, replacement);
            }

            @Override
            public ParseTree getLeftOperand(ParseTree ctx) {
                List<JavaParser.ExpressionContext> call = anyCallOperands(ctx);
                if (call != null) return call.get(0);
                List<ParseTree> equals = anyEqualsOperands(ctx);
                return equals != null ? equals.get(0) : ctx.getChild(0);
            }

            @Override
            public ParseTree getRightOperand(ParseTree ctx) {
                List<JavaParser.ExpressionContext> call = anyCallOperands(ctx);
                if (call != null) return call.get(1);
                List<ParseTree> equals = anyEqualsOperands(ctx);
                // Otherwise the right operand is always the last child
                return equals != null ? equals.get(1) : ctx.getChild(ctx.getChildCount() - 1);
            }

            @Override
            public ParseTree getOperatorToken(ParseTree ctx) {
                // For multi-token operators, we return the first token as the "point"
                if (ctx.getChildCount() >= 3) {
                    return ctx.getChild(1);
                }
                return null;
            }

            @Override
            public boolean isQuantity(ParseTree ctx) {
                if (ctx == null) return false;
                return MSLScannerAdapter.isQuantityText(getNodeText(ctx), ctx instanceof TerminalNode);
            }

            @Override
            public boolean isStringLiteral(ParseTree ctx) {
                return ctx != null && MSLScannerAdapter.isStringLiteral(getNodeText(ctx).strip(), false);
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

            // MSL's "s is null" is a null-or-empty check. Java spells it with a
            // library call (Guava's, Commons', or a C#-style name) or writes it
            // out as s == null || s.isEmpty() and variants.
            private static final java.util.Set<String> NULL_OR_EMPTY_CALLS = java.util.Set.of(
                    "string.IsNullOrEmpty", "String.IsNullOrEmpty", "Strings.isNullOrEmpty",
                    "StringUtils.isEmpty", "isNullOrEmpty");

            @Override
            public ParseTree nullCheckSubject(ParseTree ctx) {
                List<JavaParser.ExpressionContext> args = callArguments(ctx);
                if (args != null) {
                    return NULL_OR_EMPTY_CALLS.contains(calleeName(ctx)) && args.size() == 1 ? args.get(0) : null;
                }
                if (!(ctx instanceof JavaParser.BinaryOperatorExpressionContext or)
                        || or.bop == null || !or.bop.getText().equals("||")) {
                    return null;
                }
                ParseTree subject = comparedWithNull(or.expression(0));
                return subject != null && isEmptyCheckOf(or.expression(1), subject.getText()) ? subject : null;
            }

            /** s for "s == null" where s is a name or dotted path, else null. */
            private ParseTree comparedWithNull(ParseTree expr) {
                if (expr instanceof JavaParser.BinaryOperatorExpressionContext eq
                        && eq.bop != null && eq.bop.getText().equals("==")
                        && eq.expression(1).getText().equals("null")
                        && mutationtester.msl.CstNodes.isDottedName(eq.expression(0))) {
                    return eq.expression(0);
                }
                return null;
            }

            /** s.isEmpty(), s.length() == 0, s.equals("") or "".equals(s). */
            private boolean isEmptyCheckOf(ParseTree expr, String subject) {
                if (expr instanceof JavaParser.BinaryOperatorExpressionContext eq
                        && eq.bop != null && eq.bop.getText().equals("==")
                        && eq.expression(1).getText().equals("0")) {
                    return isCallOn(eq.expression(0), subject, "length", null);
                }
                return isCallOn(expr, subject, "isEmpty", null)
                        || isCallOn(expr, subject, "equals", "\"\"")
                        || isCallOn(expr, "\"\"", "equals", subject);
            }

            /** receiver.method() with no argument, or receiver.method(argument). */
            private boolean isCallOn(ParseTree expr, String receiver, String method, String argument) {
                if (!(expr instanceof JavaParser.MemberReferenceExpressionContext member)
                        || member.methodCall() == null || member.methodCall().identifier() == null
                        || !member.methodCall().identifier().getText().equals(method)
                        || !member.expression().getText().equals(receiver)) {
                    return false;
                }
                JavaParser.ExpressionListContext list = member.methodCall().arguments().expressionList();
                if (argument == null) return list == null;
                return list != null && list.expression().size() == 1 && list.expression(0).getText().equals(argument);
            }

            /** The arguments of a call foo(...) or a.b.foo(...) whose callee is a dotted name; else null. */
            private List<JavaParser.ExpressionContext> callArguments(ParseTree ctx) {
                JavaParser.MethodCallContext call = null;
                if (ctx instanceof JavaParser.MethodCallExpressionContext mc) {
                    call = mc.methodCall();
                } else if (ctx instanceof JavaParser.MemberReferenceExpressionContext member
                        && member.methodCall() != null
                        && mutationtester.msl.CstNodes.isDottedName(member.expression())) {
                    call = member.methodCall();
                }
                if (call == null || call.identifier() == null) return null;
                JavaParser.ExpressionListContext list = call.arguments().expressionList();
                return list == null ? List.of() : list.expression();
            }

            @Override
            public List<ParseTree> callArguments(ParseTree ctx, String functionName) {
                List<JavaParser.ExpressionContext> args = callArguments(ctx);
                return args != null && calleeName(ctx).equals(functionName) ? new ArrayList<>(args) : null;
            }

            /** "foo" or "a.b.foo" for a call accepted by callArguments. */
            private String calleeName(ParseTree ctx) {
                if (ctx instanceof JavaParser.MemberReferenceExpressionContext member) {
                    return member.expression().getText() + "." + member.methodCall().identifier().getText();
                }
                return ((JavaParser.MethodCallExpressionContext) ctx).methodCall().identifier().getText();
            }

            @Override
            public String notEmptyStringCheck(String subject) {
                // s != "" compares references in Java; compare values as C# does.
                return "(!\"\".equals(" + subject + "))";
            }

            // ── Statements, for Except: trees ────────────────────────────

            @Override
            public List<ParseTree> matchNode(mutationtester.msl.MSLNode pattern, ParseTree ctx) {
                switch (pattern.kind()) {
                    case mutationtester.msl.MSLNode.IF -> {
                        return matchIf(pattern, ctx, false);
                    }
                    case mutationtester.msl.MSLNode.WHILE -> {
                        if (ctx instanceof JavaParser.StatementContext s && s.WHILE() != null && s.DO() == null) {
                            return List.of(s.expression(0), s.statement(0));
                        }
                        return null;
                    }
                    case mutationtester.msl.MSLNode.FOR -> {
                        if (ctx instanceof JavaParser.StatementContext s && s.FOR() != null
                                && s.forControl().enhancedForControl() == null) {
                            JavaParser.ForControlContext control = s.forControl();
                            return java.util.Arrays.asList(control.forInit(), control.expression(),
                                    control.forUpdate, s.statement(0));
                        }
                        return null;
                    }
                    case mutationtester.msl.MSLNode.ASSIGN -> {
                        ParseTree expr = ctx instanceof JavaParser.StatementContext s ? s.statementExpression : ctx;
                        if (expr instanceof JavaParser.BinaryOperatorExpressionContext assign
                                && assign.bop != null && assign.bop.getText().equals("=")) {
                            return List.of(assign.expression(0), assign.expression(1));
                        }
                        return null;
                    }
                    case mutationtester.msl.MSLNode.BLOCK -> {
                        if (!(ctx instanceof JavaParser.StatementContext s) || s.blockLabel == null
                                || s.blockLabel.blockStatement().size() < pattern.children().size()) {
                            return null;
                        }
                        List<ParseTree> leading = new ArrayList<>();
                        for (int i = 0; i < pattern.children().size(); i++) {
                            JavaParser.BlockStatementContext statement = s.blockLabel.blockStatement(i);
                            leading.add(statement.statement() != null ? statement.statement() : statement);
                        }
                        return leading;
                    }
                    case mutationtester.msl.MSLNode.UNARY -> {
                        return ctx instanceof JavaParser.UnaryOperatorExpressionContext u
                                && u.prefix.getText().equals(pattern.value()) ? List.of(u.expression()) : null;
                    }
                    case mutationtester.msl.MSLNode.CONDITIONAL -> {
                        return ctx instanceof JavaParser.TernaryExpressionContext t
                                ? List.of(t.expression(0), t.expression(1), t.expression(2)) : null;
                    }
                    case mutationtester.msl.MSLNode.COMPOUND_ASSIGN -> {
                        return ctx instanceof JavaParser.BinaryOperatorExpressionContext b && b.bop != null
                                && b.bop.getText().equals(pattern.value()) && b.expression().size() == 2
                                ? List.of(b.expression(0), b.expression(1)) : null;
                    }
                    case mutationtester.msl.MSLNode.INDEX -> {
                        return ctx instanceof JavaParser.SquareBracketExpressionContext sq
                                ? List.of(sq.expression(0), sq.expression(1)) : null;
                    }
                    case mutationtester.msl.MSLNode.MEMBER -> {
                        return ctx instanceof JavaParser.MemberReferenceExpressionContext m && m.identifier() != null
                                ? List.of(m.expression(), m.identifier()) : null;
                    }
                    case mutationtester.msl.MSLNode.MEMBER_CALL -> {
                        if (!(ctx instanceof JavaParser.MemberReferenceExpressionContext m) || m.methodCall() == null
                                || m.methodCall().identifier() == null) {
                            return null;
                        }
                        List<ParseTree> parts = new ArrayList<>(List.of(m.expression(), m.methodCall().identifier()));
                        parts.addAll(argumentList(m.methodCall().arguments()));
                        return parts;
                    }
                    case mutationtester.msl.MSLNode.RETURN -> {
                        if (!(ctx instanceof JavaParser.StatementContext s) || s.RETURN() == null) return null;
                        return s.expression().isEmpty() ? List.of() : List.of(s.expression(0));
                    }
                    case mutationtester.msl.MSLNode.BREAK, mutationtester.msl.MSLNode.CONTINUE -> {
                        if (!(ctx instanceof JavaParser.StatementContext s)
                                || (pattern.kind().equals("break") ? s.BREAK() : s.CONTINUE()) == null) {
                            return null;
                        }
                        return s.identifier() == null ? List.of() : List.of(s.identifier());
                    }
                    case mutationtester.msl.MSLNode.THROW -> {
                        return ctx instanceof JavaParser.StatementContext s && s.THROW() != null
                                ? List.of(s.expression(0)) : null;
                    }
                    case mutationtester.msl.MSLNode.DO_WHILE -> {
                        return ctx instanceof JavaParser.StatementContext s && s.DO() != null
                                ? List.of(s.statement(0), s.expression(0)) : null;
                    }
                    case mutationtester.msl.MSLNode.FOR_EACH -> {
                        if (!(ctx instanceof JavaParser.StatementContext s) || s.FOR() == null
                                || s.forControl().enhancedForControl() == null) {
                            return null;
                        }
                        JavaParser.EnhancedForControlContext e = s.forControl().enhancedForControl();
                        return List.of(e.variableDeclaratorId(), e.expression(), s.statement(0));
                    }
                    case mutationtester.msl.MSLNode.TRY -> {
                        if (!(ctx instanceof JavaParser.StatementContext s) || s.TRY() == null
                                || s.resourceSpecification() != null) {
                            return null;
                        }
                        return java.util.Arrays.asList(s.block(),
                                s.catchClause().isEmpty() ? null : s.catchClause(0).block(),
                                s.finallyBlock() == null ? null : s.finallyBlock().block());
                    }
                    case mutationtester.msl.MSLNode.CASE, mutationtester.msl.MSLNode.DEFAULT_CASE -> {
                        if (!(ctx instanceof JavaParser.SwitchBlockStatementGroupContext g) || g.switchLabel().size() != 1) {
                            return null;
                        }
                        JavaParser.SwitchLabelContext label = g.switchLabel(0);
                        ParseTree body = mutationtester.msl.CstNodes.span(g.blockStatement());
                        if (pattern.kind().equals("case")) {
                            return label.CASE() != null && label.constantExpression != null
                                    ? List.of(label.constantExpression, body) : null;
                        }
                        return label.DEFAULT() != null ? List.of(body) : null;
                    }
                    case mutationtester.msl.MSLNode.DECLARE -> {
                        if (!(ctx instanceof JavaParser.LocalVariableDeclarationContext d)) return null;
                        if (d.VAR() != null) return List.of(d.identifier(), d.expression());
                        List<JavaParser.VariableDeclaratorContext> declarators = d.variableDeclarators().variableDeclarator();
                        if (declarators.size() != 1 || declarators.get(0).variableInitializer() == null) return null;
                        return List.of(declarators.get(0).variableDeclaratorId(), declarators.get(0).variableInitializer());
                    }
                    case mutationtester.msl.MSLNode.COLLECTION -> {
                        return ctx instanceof JavaParser.ArrayInitializerContext a ? new ArrayList<>(a.variableInitializer()) : null;
                    }
                    case mutationtester.msl.MSLNode.LAMBDA -> {
                        if (!(ctx instanceof JavaParser.LambdaExpressionContext l)) return null;
                        JavaParser.LambdaParametersContext p = l.lambdaParameters();
                        List<ParseTree> parts = new ArrayList<>(p.formalParameterList() != null ? p.formalParameterList().formalParameter()
                                : p.lambdaLVTIList() != null ? p.lambdaLVTIList().lambdaLVTIParameter() : p.identifier());
                        parts.add(l.lambdaBody());
                        return parts;
                    }
                    case mutationtester.msl.MSLNode.FUNCTION -> {
                        if (!(ctx instanceof JavaParser.MethodDeclarationContext m) || m.methodBody().block() == null) return null;
                        List<ParseTree> parts = new ArrayList<>(List.of(m.identifier()));
                        JavaParser.FormalParametersContext fp = m.formalParameters();
                        if (fp.formalParameter() != null) parts.add(fp.formalParameter());
                        for (JavaParser.FormalParameterListContext list : fp.formalParameterList()) parts.addAll(list.formalParameter());
                        parts.add(m.methodBody().block());
                        return parts;
                    }
                    case mutationtester.msl.MSLNode.PARAMETER -> {
                        return ctx instanceof JavaParser.FormalParameterContext p ? List.of(p.variableDeclaratorId()) : null;
                    }
                    case mutationtester.msl.MSLNode.TYPE_CHECK -> {
                        return ctx instanceof JavaParser.InstanceOfOperatorExpressionContext i && i.typeType() != null
                                ? List.of(i.expression(), i.typeType()) : null;
                    }
                    case mutationtester.msl.MSLNode.CAST -> {
                        return ctx instanceof JavaParser.CastExpressionContext c && c.typeType().size() == 1
                                ? List.of(c.typeType(0), c.expression()) : null;
                    }
                    case mutationtester.msl.MSLNode.FIELD -> {
                        if (!(ctx instanceof JavaParser.FieldDeclarationContext f)
                                || f.variableDeclarators().variableDeclarator().size() != 1) {
                            return null;
                        }
                        JavaParser.VariableDeclaratorContext v = f.variableDeclarators().variableDeclarator(0);
                        return v.variableInitializer() == null ? List.of(v.variableDeclaratorId())
                                : List.of(v.variableDeclaratorId(), v.variableInitializer());
                    }
                    case mutationtester.msl.MSLNode.INITIALIZED_NEW -> {
                        // new T(args) { members }: an anonymous class
                        if (!(ctx instanceof JavaParser.ObjectCreationExpressionContext o)
                                || o.creator().classCreatorRest() == null || o.creator().classCreatorRest().classBody() == null) {
                            return null;
                        }
                        List<ParseTree> parts = new ArrayList<>(List.of(o.creator().createdName()));
                        parts.addAll(o.creator().classCreatorRest().classBody().classBodyDeclaration());
                        return parts;
                    }
                    case mutationtester.msl.MSLNode.ASSERT -> {
                        return ctx instanceof JavaParser.StatementContext s && s.ASSERT() != null ? List.of(s.expression(0)) : null;
                    }
                    case mutationtester.msl.MSLNode.WITH -> {
                        if (!(ctx instanceof JavaParser.StatementContext s)) return null;
                        if (s.SYNCHRONIZED() != null) return List.of(s.expression(0), s.block());
                        return s.TRY() != null && s.resourceSpecification() != null && s.catchClause().isEmpty()
                                && s.finallyBlock() == null ? List.of(s.resourceSpecification(), s.block()) : null;
                    }
                    case mutationtester.msl.MSLNode.YIELD -> {
                        return ctx instanceof JavaParser.StatementContext s && s.YIELD() != null ? List.of(s.expression(0)) : null;
                    }
                    case mutationtester.msl.MSLNode.LABELED -> {
                        return ctx instanceof JavaParser.StatementContext s && s.identifierLabel != null
                                ? List.of(s.identifierLabel, s.statement(0)) : null;
                    }
                    case mutationtester.msl.MSLNode.ARM -> {
                        if (!(ctx instanceof JavaParser.SwitchLabeledRuleContext r) || r.CASE() == null || r.ARROW() == null
                                || r.expressionList() == null || r.expressionList().expression().size() != 1) {
                            return null;
                        }
                        return List.of(r.expressionList().expression(0), r.switchRuleOutcome());
                    }
                    case mutationtester.msl.MSLNode.ANNOTATION -> {
                        return ctx instanceof JavaParser.AnnotationContext a && a.annotationFieldValues() == null
                                ? List.of(a.qualifiedName()) : null;
                    }
                    default -> {
                        return MSLScannerAdapter.super.matchNode(pattern, ctx);
                    }
                }
            }

            /** foo(a, b): [foo, a, b]. */
            @Override
            public List<ParseTree> callParts(ParseTree ctx) {
                if (!(ctx instanceof JavaParser.MethodCallExpressionContext call)
                        || call.methodCall().identifier() == null) {
                    return null;
                }
                List<ParseTree> parts = new ArrayList<>(List.of(call.methodCall().identifier()));
                parts.addAll(argumentList(call.methodCall().arguments()));
                return parts;
            }

            private List<ParseTree> argumentList(JavaParser.ArgumentsContext arguments) {
                return arguments.expressionList() == null ? List.of()
                        : new ArrayList<>(arguments.expressionList().expression());
            }

            // ── Literals and type names ──────────────────────────────────────

            @Override
            public mutationtester.msl.ValueKind valueKind(ParseTree ctx) {
                return Literals.valueKind(Literals.Lang.JAVA, ctx);
            }

            @Override
            public String convert(ParseTree node, String fromType, String toType, String toCondition, ParseTree ctx) {
                mutationtester.msl.ValueKind from = valueKind(node);
                if (from == null) return null;
                // int n = 42.0; and a[1.0] don't compile: keep integers where the code needs one.
                if (!from.typeName() && from.fits("integer") && !toType.equals("integer")
                        && !toType.equals("based_integer") && Literals.needsInteger(node)) {
                    return null;
                }
                return Literals.convert(Literals.Lang.JAVA, getNodeText(node).strip(), from, toType, toCondition);
            }

            // ── Statements ────────────────────────────────────────────────

            @Override
            public List<ParseTree> ifParts(ParseTree ctx) {
                if (!(ctx instanceof JavaParser.StatementContext s) || s.IF() == null) return null;
                return java.util.Arrays.asList(s.expression(0), s.statement(0), s.ELSE() == null ? null : s.statement(1));
            }

            @Override
            public boolean isBlock(ParseTree ctx) {
                return ctx instanceof JavaParser.BlockContext
                        || ctx instanceof JavaParser.StatementContext s && s.blockLabel != null;
            }

            /** Keeps the declared type: int n = 0, not var n = 0. */
            @Override
            public String renderDeclaration(String name, String value, ParseTree ctx) {
                String type = ctx instanceof JavaParser.LocalVariableDeclarationContext d && d.typeType() != null
                        ? d.typeType().getText() : "var";
                return type + " " + name + " = " + value;
            }

            @Override
            public String renderForEach(String name, String collection, String body, ParseTree ctx) {
                return "for (var " + name + " : " + collection + ") " + body;
            }

            // ── Collections, functions and types ────────────────────────────

            @Override
            public boolean isLambda(ParseTree ctx) {
                return ctx instanceof JavaParser.LambdaExpressionContext;
            }

            @Override
            public boolean isTypeName(ParseTree ctx) {
                return ctx instanceof JavaParser.TypeTypeContext || ctx instanceof JavaParser.CreatedNameContext;
            }

            @Override
            public boolean isField(ParseTree ctx) {
                return ctx instanceof JavaParser.FieldDeclarationContext;
            }

            /** Java writes array contents in braces: int[] a = {1, 2}. */
            @Override
            public String renderCollection(List<String> elements, ParseTree ctx) {
                return ctx instanceof JavaParser.ArrayInitializerContext ? "{" + String.join(", ", elements) + "}" : null;
            }

            @Override
            public String emptyCollection(ParseTree ctx) {
                return ctx instanceof JavaParser.ArrayInitializerContext ? "{}" : null;
            }

            @Override
            public String renderLambda(List<String> parameters, String body, ParseTree ctx) {
                return "(" + String.join(", ", parameters) + ") -> " + body;
            }

            /** Keeps the declared type; Java parameters have no defaults. */
            @Override
            public String renderParameter(String name, String defaultValue, ParseTree ctx) {
                if (defaultValue != null || !(ctx instanceof JavaParser.FormalParameterContext p)) return null;
                return p.typeType().getText() + " " + name;
            }

            @Override
            public String renderField(String name, String value, ParseTree ctx) {
                if (!(ctx instanceof JavaParser.FieldDeclarationContext f)) return null;
                return f.typeType().getText() + " " + name + (value == null ? "" : " = " + value) + ";";
            }

            @Override
            public String renderCast(String type, String value, ParseTree ctx) {
                return "(" + type + ") " + value;
            }

            /** new T(args) {} without its body is new T(args). */
            @Override
            public String renderInitializedNew(String type, List<String> initializers, ParseTree ctx) {
                if (!initializers.isEmpty() || !(ctx instanceof JavaParser.ObjectCreationExpressionContext o)) return null;
                return "new " + type + getNodeText(o.creator().classCreatorRest().arguments());
            }

            // ── Remaining statements ────────────────────────────────────────

            @Override
            public String renderKeywordStatement(String kind, String value, ParseTree ctx) {
                return switch (kind) {
                    case "assert" -> "assert " + value;
                    case "annotation" -> "@" + value;
                    default -> null;
                };
            }

            @Override
            public String renderArm(String pattern, String value, ParseTree ctx) {
                return "case " + pattern + " -> " + value;
            }

            // ── Increments ───────────────────────────────────────────────

            @Override
            public ParseTree incrementOperand(String operator, ParseTree ctx) {
                if (ctx instanceof JavaParser.PostIncrementDecrementOperatorExpressionContext post
                        && post.postfix.getText().equals(operator)) {
                    return post.expression();
                }
                if (ctx instanceof JavaParser.UnaryOperatorExpressionContext pre
                        && pre.prefix.getText().equals(operator)) {
                    return pre.expression();
                }
                return null;
            }

            @Override
            public ParseTree expressionStatement(ParseTree ctx) {
                return ctx.getParent() instanceof JavaParser.StatementContext s && s.statementExpression == ctx
                        ? s : null;
            }

            @Override
            public boolean discardsValue(ParseTree ctx) {
                // for (...; ...; i++, j--)
                return expressionStatement(ctx) != null
                        || ctx.getParent() instanceof JavaParser.ExpressionListContext list
                            && list.getParent() instanceof JavaParser.ForControlContext control
                            && control.forUpdate == list;
            }

            @Override
            public java.util.Set<String> supportedKinds() {
                return java.util.Set.of("binary", "predicate", "literal", "identifier", "call",
                        "if", "while", "for", "assign", "block", "increment", "return", "break", "continue", "declare", "for_each", "do_while", "case", "default_case", "throw", "try",
                        "unary", "conditional", "compound_assign", "index", "member", "member_call",
                        "collection", "lambda", "function", "parameter", "type_check", "cast", "field", "initialized_new",
                        "assert", "with", "yield", "labeled", "arm", "annotation");
            }

            @Override
            public java.util.Set<String> supportedPlaceholders() {
                return java.util.Set.of("quantity", "match", "statement", "invocation_statement",
                        "return_value", "constructor_call", "non_void_method_call",
                        "equality_condition", "order_condition",
                        "number", "integer", "float", "exponential", "based_integer", "string", "char", "boolean",
                        "condition", "name", "arguments", "block", "contains", "collection", "lambda", "type", "field");
            }

            @Override
            public boolean isInvocationStatement(ParseTree ctx) {
                // An expression statement whose expression is a method call:
                // foo(); or obj.foo(); - not a declaration such as void foo();
                if (!(ctx instanceof JavaParser.StatementContext stmt) || stmt.statementExpression == null) {
                    return false;
                }
                JavaParser.ExpressionContext expr = stmt.statementExpression;
                boolean isCall = expr instanceof JavaParser.MethodCallExpressionContext
                        || (expr instanceof JavaParser.MemberReferenceExpressionContext member
                            && member.methodCall() != null);
                return isCall && !isOnlyStatementOfTryWithCatch(stmt);
            }

            /**
             * In try { foo(); } catch (SomeCheckedException e), removing foo()
             * leaves a catch javac rejects as unreachable.
             */
            private boolean isOnlyStatementOfTryWithCatch(JavaParser.StatementContext stmt) {
                if (!(stmt.getParent() instanceof JavaParser.BlockStatementContext blockStmt)
                        || !(blockStmt.getParent() instanceof JavaParser.BlockContext block)
                        || !(block.getParent() instanceof JavaParser.StatementContext tryStmt)) {
                    return false;
                }
                return tryStmt.TRY() != null && !tryStmt.catchClause().isEmpty()
                        && block.blockStatement().size() == 1;
            }

            @Override
            public String getEmptyStatement(ParseTree statement) {
                // "case X -> foo();" needs a block; a bare ';' isn't allowed there.
                for (ParseTree p = statement.getParent(); p != null; p = p.getParent()) {
                    if (p instanceof JavaParser.BlockContext) return ";";
                    if (p instanceof JavaParser.SwitchRuleOutcomeContext) return "{}";
                }
                return ";";
            }

            // ── PIT-style placeholders ───────────────────────────────────

            @Override
            public boolean matchesPlaceholder(String type, ParseTree ctx) {
                return switch (type) {
                    case "statement" -> ctx instanceof JavaParser.StatementContext || ctx instanceof JavaParser.BlockContext
                            || mutationtester.msl.CstNodes.isSpan(ctx);
                    case "return_value" -> returnStatementOf(ctx) != null;
                    case "constructor_call" -> isConstructorCall(ctx);
                    case "non_void_method_call" -> assignmentOf(ctx) != null;
                    // javac rejects while (false) and code after while (true) as
                    // unreachable, so only if-conditions are removed.
                    case "equality_condition" -> isIfCondition(ctx)
                            && mutationtester.msl.CstNodes.anyTokenContains(ctx, "==", "!=");
                    case "order_condition" -> isIfCondition(ctx)
                            && mutationtester.msl.CstNodes.anyTokenContains(ctx, "<", ">");
                    default -> MSLScannerAdapter.super.matchesPlaceholder(type, ctx);
                };
            }

            /** The return statement whose value ctx is, else null. */
            private JavaParser.StatementContext returnStatementOf(ParseTree ctx) {
                return ctx instanceof JavaParser.ExpressionContext
                        && ctx.getParent() instanceof JavaParser.StatementContext s
                        && s.RETURN() != null && s.expression(0) == ctx ? s : null;
            }

            /** new T(...), but not an anonymous class or a thrown exception, which can't become null. */
            private boolean isConstructorCall(ParseTree ctx) {
                if (!(ctx instanceof JavaParser.ObjectCreationExpressionContext creation)
                        || creation.creator().classCreatorRest() == null
                        || creation.creator().classCreatorRest().classBody() != null) {
                    return false;
                }
                return !(ctx.getParent() instanceof JavaParser.StatementContext s && s.THROW() != null);
            }

            private boolean isIfCondition(ParseTree ctx) {
                return ctx instanceof JavaParser.ExpressionContext
                        && ctx.getParent() instanceof JavaParser.StatementContext s
                        && s.IF() != null && s.expression(0) == ctx;
            }

            /**
             * For a call that starts the value of a plain assignment or an
             * initialized declaration (v = f(x), int v = a.b(c).d()), the
             * assignment or declarator; else null. "var v = f()" is left alone,
             * since "var v = null" doesn't compile.
             */
            private ParseTree assignmentOf(ParseTree ctx) {
                if (callArguments(ctx) == null) return null;
                ParseTree value = ctx;
                while (value.getParent() instanceof JavaParser.ExpressionContext parent
                        && parent.getChild(0) == value) {
                    value = parent;
                }
                ParseTree parent = value.getParent();
                if (parent instanceof JavaParser.BinaryOperatorExpressionContext assign
                        && assign.bop != null && assign.bop.getText().equals("=")
                        && assign.expression(1) == value) {
                    return assign;
                }
                if (parent instanceof JavaParser.VariableInitializerContext init
                        && init.getParent() instanceof JavaParser.VariableDeclaratorContext declarator) {
                    return declarator;
                }
                // try (Stream<Path> s = Files.walk(root)) - but not "try (var s = ...)"
                if (parent instanceof JavaParser.ResourceContext resource && resource.VAR() == null
                        && resource.expression() == value) {
                    return resource;
                }
                return null;
            }

            @Override
            public String renderValue(String placeholderType, ParseTree ctx) {
                switch (placeholderType) {
                    case "empty_value" -> {
                        // A value of the method's declared return type.
                        String returnType = enclosingReturnType(ctx);
                        return returnType == null
                                ? TypedEmptyValues.forExpression(getNodeText(ctx), "null", "false")
                                : TypedEmptyValues.forType(returnType, false);
                    }
                    case "default_value" -> {
                        ParseTree assignment = assignmentOf(ctx);
                        String type = assignment == null ? null : assignedType(assignment);
                        if (type == null) return "null";
                        String value = TypedEmptyValues.forType(type, false);
                        // A number is the default for numbers, not an "empty" value.
                        return value == null ? null
                                : value.equals("\"\"") || value.equals("Optional.empty()") ? "null" : value;
                    }
                    default -> {
                        return null;
                    }
                }
            }

            /** The declared return type of the method around ctx, or null. */
            private String enclosingReturnType(ParseTree ctx) {
                for (ParseTree p = ctx.getParent(); p != null; p = p.getParent()) {
                    if (p instanceof JavaParser.MethodDeclarationContext m) return m.typeTypeOrVoid().getText();
                    if (p instanceof JavaParser.InterfaceCommonBodyDeclarationContext m) {
                        return m.typeTypeOrVoid().getText();
                    }
                }
                return null;
            }

            /**
             * The declared type of the variable being assigned: from the
             * declaration itself, or for a plain "v = ...", from the nearest
             * earlier declaration of v. Null when unknown.
             */
            private String assignedType(ParseTree assignment) {
                if (assignment instanceof JavaParser.ResourceContext resource) {
                    return resource.classOrInterfaceType().getText();
                }
                if (assignment instanceof JavaParser.VariableDeclaratorContext declarator) {
                    ParseTree declarators = declarator.getParent();
                    ParseTree owner = declarators == null ? null : declarators.getParent();
                    if (owner instanceof JavaParser.LocalVariableDeclarationContext local && local.typeType() != null) {
                        return local.typeType().getText();
                    }
                    if (owner instanceof JavaParser.FieldDeclarationContext field) return field.typeType().getText();
                    return null;
                }
                JavaParser.ExpressionContext target = ((JavaParser.BinaryOperatorExpressionContext) assignment).expression(0);
                String name = null;
                if (target instanceof JavaParser.PrimaryExpressionContext primary && primary.primary().identifier() != null) {
                    name = primary.primary().identifier().getText();
                } else if (target instanceof JavaParser.MemberReferenceExpressionContext member && member.identifier() != null) {
                    name = member.identifier().getText();
                }
                if (name == null) return null;
                int before = ((ParserRuleContext) assignment).getStart().getTokenIndex();
                String type = null;
                int nearest = -1;
                for (Object[] declaration : declarations()) {
                    int index = (Integer) declaration[0];
                    if (index < before && index > nearest && declaration[1].equals(name)) {
                        nearest = index;
                        type = (String) declaration[2];
                    }
                }
                return type;
            }

            private List<Object[]> declarations;

            /** {token index, name, type} for every typed variable, field, parameter and catch variable. */
            private List<Object[]> declarations() {
                if (declarations == null) {
                    declarations = new ArrayList<>();
                    collectDeclarations(tree);
                }
                return declarations;
            }

            private void collectDeclarations(ParseTree node) {
                if (node instanceof JavaParser.LocalVariableDeclarationContext local && local.typeType() != null) {
                    addDeclarators(local.variableDeclarators(), local.typeType().getText());
                } else if (node instanceof JavaParser.FieldDeclarationContext field) {
                    addDeclarators(field.variableDeclarators(), field.typeType().getText());
                } else if (node instanceof JavaParser.FormalParameterContext param) {
                    addDeclaration(param.variableDeclaratorId().identifier(), param.typeType().getText());
                } else if (node instanceof JavaParser.CatchClauseContext caught) {
                    addDeclaration(caught.identifier(), caught.catchType().getText());
                }
                for (int i = 0; i < node.getChildCount(); i++) collectDeclarations(node.getChild(i));
            }

            private void addDeclarators(JavaParser.VariableDeclaratorsContext declarators, String type) {
                if (declarators == null) return;
                for (JavaParser.VariableDeclaratorContext d : declarators.variableDeclarator()) {
                    addDeclaration(d.variableDeclaratorId().identifier(), type);
                }
            }

            private void addDeclaration(JavaParser.IdentifierContext id, String type) {
                if (id != null) declarations.add(new Object[]{id.getStart().getTokenIndex(), id.getText(), type});
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

            @Override
            public ParseTree getRootContext() {
                return tree;
            }
        };
    }
}
