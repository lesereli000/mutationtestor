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
import mutationtester.JavaParserBaseVisitor;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.msl.MSLScannerAdapter;

public class JavaMutationScanner {
    /**
     * Scan a Java file for mutations using AST parsing
     */
    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        return scanFile(file, type, false);
    }

    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type, boolean showViewer)
            throws IOException {
        if (type == Mutator.MutatorType.CONDITIONAL_TRUE
                || type == Mutator.MutatorType.CONDITIONAL_FALSE
                || type == Mutator.MutatorType.REMOVE_LOGICAL_NEGATION
                || type == Mutator.MutatorType.EMPTY_RETURNS
                || type == Mutator.MutatorType.VML_VOID_METHOD_CALL
                || type == Mutator.MutatorType.NON_VOID_METHOD_CALLS
                || type == Mutator.MutatorType.CONSTRUCTOR_CALLS) {
            return UniversalPitMutationScanner.scan(file, type);
        }
        String sourceCode = Files.readString(file.toPath());

        // Parse Java using ANTLR (same as MyParser)
        CharStream input = CharStreams.fromString(sourceCode);
        JavaLexer lexer = new JavaLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);

        // Get parse tree starting from program() rule (same as MyParser)
        ParseTree tree = parser.compilationUnit();

        if (showViewer) {
            TreeViewer viewer = new TreeViewer(Arrays.asList(parser.getRuleNames()), tree);
            viewer.open();
        }

        // Create visitor based on mutation type
        JavaMutationScanner.JavaMutationVisitor visitor = new JavaMutationScanner.JavaMutationVisitor(file, type);
        visitor.visit(tree);

        return visitor.getMutationPoints();
    }

    /**
     * Visitor that walks the Java AST and finds mutation points
     */
    private static class JavaMutationVisitor extends JavaParserBaseVisitor<Void> {

        private final File sourceFile;
        private final Mutator.MutatorType mutationType;
        private final List<MutationPoint> mutationPoints;

        public JavaMutationVisitor(File sourceFile, Mutator.MutatorType mutationType) {
            this.sourceFile = sourceFile;
            this.mutationType = mutationType;
            this.mutationPoints = new ArrayList<>();
        }

        /**
         * Visit binary operator expressions (+, -, &&, ||, *, /, etc.)
         */
        @Override
        public Void visitBinaryOperatorExpression(JavaParser.BinaryOperatorExpressionContext ctx) {
            // If there aren't exactly 3 children, it's not a simple binary operator (e.g.,
            // >> or >>>)
            if (ctx.getChildCount() != 3) {
                return super.visitBinaryOperatorExpression(ctx);
            }

            // Walk through all children of this expression
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                // Check if this child is a terminal node (operator)
                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    switch (operator) {
                        // AOR_PLUS_TO_MINUS: mutate + to -
                        case "+":
                            if (mutationType == Mutator.MutatorType.AOR_PLUS_TO_MINUS) {
                                if (!containsStringLiteral(ctx)) {
                                    addMutation(token, "+", "-");
                                }
                            }
                            break;

                        // AOR_MINUS_TO_PLUS: mutate - to +
                        case "-":
                            if (mutationType == Mutator.MutatorType.AOR_MINUS_TO_PLUS) {
                                addMutation(token, "-", "+");
                            }
                            break;

                        case "*":
                            if (mutationType == Mutator.MutatorType.AOR_MINUS_TO_PLUS) {
                                addMutation(token, "*", "/");
                            }
                            break;

                        case "/":
                            if (mutationType == Mutator.MutatorType.AOR_MINUS_TO_PLUS) {
                                addMutation(token, "/", "*");
                            }
                            break;

                        case "&&":
                            if (mutationType == Mutator.MutatorType.LOGICAL_AND_TO_OR) {
                                addMutation(token, "&&", "||");
                            }
                            break;

                        case "||":
                            if (mutationType == Mutator.MutatorType.LOGICAL_OR_TO_AND) {
                                addMutation(token, "||", "&&");
                            }
                            break;

                        case ">":
                            if (mutationType == Mutator.MutatorType.ROR_GT_TO_GE) {
                                addMutation(token, ">", ">=");
                            }
                            break;

                        case ">=":
                            if (mutationType == Mutator.MutatorType.ROR_GE_TO_GT) {
                                addMutation(token, ">=", ">");
                            }
                            break;

                        case "<":
                            if (mutationType == Mutator.MutatorType.ROR_LT_TO_LE) {
                                addMutation(token, "<", "<=");
                            }
                            break;

                        case "<=":
                            if (mutationType == Mutator.MutatorType.ROR_LE_TO_LT) {
                                addMutation(token, "<=", "<");
                            }
                            break;

                        case "&":
                            if (mutationType == Mutator.MutatorType.BOR_AND_TO_OR) {
                                addMutation(token, "&", "|");
                            }
                            break;

                        default:
                            System.out.println("Unrecognized operator: " + operator);
                            break;

                    }
                }
            }

            // Continue visiting children
            return visitChildren(ctx);
        }

        /**
         * Visit primitive types (int, long, double, float, etc.)
         */
        @Override
        public Void visitPrimitiveType(JavaParser.PrimitiveTypeContext ctx) {
            String typeText = ctx.getText();
            Token token = ctx.getStart();

            // Mapping of "from" type to "to" type based on mutator
            if (mutationType == Mutator.MutatorType.TYPE_INT_TO_LONG && typeText.equals("int")) {
                addMutation(token, "int", "long");
            } else if (mutationType == Mutator.MutatorType.TYPE_LONG_TO_INT && typeText.equals("long")) {
                addMutation(token, "long", "int");
            } else if (mutationType == Mutator.MutatorType.TYPE_INT_TO_DOUBLE && typeText.equals("int")) {
                addMutation(token, "int", "double");
            } else if (mutationType == Mutator.MutatorType.TYPE_INT_TO_FLOAT && typeText.equals("int")) {
                addMutation(token, "int", "float");
            } else if (mutationType == Mutator.MutatorType.TYPE_DOUBLE_TO_INT && typeText.equals("double")) {
                addMutation(token, "double", "int");
            } else if (mutationType == Mutator.MutatorType.TYPE_FLOAT_TO_INT && typeText.equals("float")) {
                addMutation(token, "float", "int");
            }

            return visitChildren(ctx);
        }

        private void addMutation(Token token, String operator, String replacement) {
            MutationPoint point = new MutationPoint(
                    sourceFile,
                    token.getLine(),
                    token.getCharPositionInLine(),
                    operator,
                    replacement,
                    mutationType);
            mutationPoints.add(point);
        }

        private boolean containsStringLiteral(ParseTree tree) {
            if (tree instanceof TerminalNode terminalNode) {
                int tokenType = terminalNode.getSymbol().getType();
                return tokenType == JavaLexer.STRING_LITERAL || tokenType == JavaLexer.TEXT_BLOCK;
            }

            for (int i = 0; i < tree.getChildCount(); i++) {
                if (containsStringLiteral(tree.getChild(i))) {
                    return true;
                }
            }
            return false;
        }

        public List<MutationPoint> getMutationPoints() {
            // System.out.println(mutationPoints);
            return mutationPoints;
        }
    }
    public static MSLScannerAdapter createAdapter(File file) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        // Pre-split for O(1) line lookups in getSourceLine()
        // ANTLR line numbers are 1-based; index 0 = line 1
        final String[] sourceLines = sourceCode.split("\n", -1);
        CharStream input = CharStreams.fromString(sourceCode);
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

            // Java spells string.IsNullOrEmpty(s) as s == null || s.isEmpty() and
            // variants (Guava's and Commons' library calls are matched as calls).
            private static final List<java.util.regex.Pattern> NULL_OR_EMPTY_IDIOMS = List.of(
                    java.util.regex.Pattern.compile(MSLScannerAdapter.NULL_CHECK_SUBJECT + "\\s*==\\s*null\\s*\\|\\|\\s*\\1\\.isEmpty\\(\\)"),
                    java.util.regex.Pattern.compile(MSLScannerAdapter.NULL_CHECK_SUBJECT + "\\s*==\\s*null\\s*\\|\\|\\s*\\1\\.length\\(\\)\\s*==\\s*0"),
                    java.util.regex.Pattern.compile(MSLScannerAdapter.NULL_CHECK_SUBJECT + "\\s*==\\s*null\\s*\\|\\|\\s*\\1\\.equals\\(\"\"\\)"),
                    java.util.regex.Pattern.compile(MSLScannerAdapter.NULL_CHECK_SUBJECT + "\\s*==\\s*null\\s*\\|\\|\\s*\"\"\\.equals\\(\\1\\)"));

            @Override
            public String getNullOrEmptySubject(ParseTree ctx) {
                return MSLScannerAdapter.matchNullOrEmptySubject(getNodeText(ctx), NULL_OR_EMPTY_IDIOMS);
            }

            @Override
            public String notEmptyStringCheck(String subject) {
                // s != "" compares references in Java; compare values as C# does.
                return "(!\"\".equals(" + subject + "))";
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

            @Override
            public String getSingleArgumentText(ParseTree ctx, String functionName) {
                String text = getNodeText(ctx);
                if (text == null) return null;
                String inner = MSLScannerAdapter.argumentText(text, functionName);
                if (inner == null || inner.isEmpty() || hasTopLevelComma(inner)) {
                    return null;
                }
                return inner;
            }

            @Override
            public List<String> getFunctionAliases(String canonicalFunctionName) {
                if ("string.IsNullOrEmpty".equals(canonicalFunctionName)) {
                    return List.of(
                            "string.IsNullOrEmpty",
                            "String.IsNullOrEmpty",
                            "Strings.isNullOrEmpty",
                            "StringUtils.isEmpty",
                            "isNullOrEmpty"
                    );
                }
                return List.of(canonicalFunctionName);
            }

            private boolean hasTopLevelComma(String text) {
                int parenDepth = 0;
                int bracketDepth = 0;
                int braceDepth = 0;
                boolean inString = false;
                char stringDelimiter = '\0';

                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (i > 0 && text.charAt(i - 1) == '\\') {
                        continue;
                    }
                    if (inString) {
                        if (c == stringDelimiter) {
                            inString = false;
                            stringDelimiter = '\0';
                        }
                        continue;
                    }
                    if (c == '"' || c == '\'') {
                        inString = true;
                        stringDelimiter = c;
                        continue;
                    }
                    switch (c) {
                        case '(' -> parenDepth++;
                        case ')' -> parenDepth--;
                        case '[' -> bracketDepth++;
                        case ']' -> bracketDepth--;
                        case '{' -> braceDepth++;
                        case '}' -> braceDepth--;
                        case ',' -> {
                            if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                                return true;
                            }
                        }
                        default -> {
                        }
                    }
                }
                return false;
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
            public String getSourceLine(ParseTree ctx) {
                ParseTree p = ctx;
                while (p != null && !(p instanceof mutationtester.JavaParser.StatementContext)) {
                    p = p.getParent();
                }
                if (p != null) {
                    org.antlr.v4.runtime.ParserRuleContext prc = (org.antlr.v4.runtime.ParserRuleContext) p;
                    if (prc.getStart() != null && prc.getStop() != null) {
                        int start = prc.getStart().getStartIndex();
                        int stop = prc.getStop().getStopIndex();
                        if (start >= 0 && stop >= start) {
                            return prc.getStart().getInputStream().getText(new org.antlr.v4.runtime.misc.Interval(start, stop));
                        }
                    }
                }
                
                int[] pos = getTokenPosition(ctx);
                int lineIndex = pos[0] - 1; // ANTLR line numbers are 1-based
                if (lineIndex >= 0 && lineIndex < sourceLines.length) {
                    return sourceLines[lineIndex];
                }
                return "";
            }

            @Override
            public ParseTree getRootContext() {
                return tree;
            }
        };
    }
}
