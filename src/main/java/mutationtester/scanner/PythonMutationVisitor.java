package mutationtester.scanner;

import mutationtester.*;
import mutationtester.model.Mutation;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PythonMutationVisitor extends PythonParserBaseVisitor<Void> {

    private final File sourceFile;
    private final String sourceCode;
    private final Mutator.MutatorType mutationType;
    private final List<MutationPoint> mutationPoints = new ArrayList<>();
    private final Set<String> seenMutations = new HashSet<>();

    public PythonMutationVisitor(File sourceFile, String sourceCode, Mutator.MutatorType mutationType) {
        this.sourceFile = sourceFile;
        this.sourceCode = sourceCode;
        this.mutationType = mutationType;
    }

    @Override
    public Void visitExpr_stmt(PythonParser.Expr_stmtContext ctx) {
        if (ctx.assign_part() != null && ctx.assign_part().COLON() != null && ctx.assign_part().test() != null) {
            maybeAddTypeSwapMutation(ctx.assign_part().test().getText().trim(), ctx.assign_part().test().getStart());
        }

        return visitChildren(ctx);
    }

    @Override
    public Void visitNamed_parameter(PythonParser.Named_parameterContext ctx) {
        if (ctx.test() != null) {
            maybeAddTypeSwapMutation(ctx.test().getText().trim(), ctx.test().getStart());
        }

        return visitChildren(ctx);
    }

    @Override
    public Void visitFuncdef(PythonParser.FuncdefContext ctx) {
        for (int i = 0; i < ctx.getChildCount() - 1; i++) {
            ParseTree child = ctx.getChild(i);
            if (!(child instanceof TerminalNode terminalNode)) {
                continue;
            }

            Token token = terminalNode.getSymbol();
            if (token.getType() != PythonLexer.ARROW) {
                continue;
            }

            ParseTree typeNode = ctx.getChild(i + 1);
            if (typeNode instanceof PythonParser.TestContext returnType) {
                maybeAddTypeSwapMutation(returnType.getText().trim(), returnType.getStart());
            }
            break;
        }

        return visitChildren(ctx);
    }

    @Override
    public Void visitExpr(PythonParser.ExprContext ctx) {
        if (ctx.op == null) {
            return visitChildren(ctx);
        }

        String operator = ctx.op.getText();
        if (mutationType == Mutator.MutatorType.AOR_PLUS_TO_MINUS && operator.equals("+")) {
            addMutation(ctx.op, "+", "-");
        } else if (mutationType == Mutator.MutatorType.AOR_MINUS_TO_PLUS && operator.equals("-")) {
            addMutation(ctx.op, "-", "+");
        } else if (mutationType == Mutator.MutatorType.AOR_MULTIPLY_TO_DIVIDE && operator.equals("*")) {
            addMutation(ctx.op, "*", "/");
        } else if (mutationType == Mutator.MutatorType.AOR_DIVIDE_TO_MULTIPLY && operator.equals("/")) {
            addMutation(ctx.op, "/", "*");
        } else if (mutationType == Mutator.MutatorType.AOR_MODULO_TO_MULTIPLY && operator.equals("%")) {
            addMutation(ctx.op, "%", "*");
        } else if (mutationType == Mutator.MutatorType.AOR_FLOOR_DIV_TO_DIV && operator.equals("//")) {
            addMutation(ctx.op, "//", "/");
        } else if (mutationType == Mutator.MutatorType.AOR_POWER_TO_MULTIPLY && operator.equals("**")) {
            addMutation(ctx.op, "**", "*");
        } else if (mutationType == Mutator.MutatorType.BOR_AND_TO_OR && operator.equals("&")) {
            addMutation(ctx.op, "&", "|");
        } else if (mutationType == Mutator.MutatorType.BOR_OR_TO_AND && operator.equals("|")) {
            addMutation(ctx.op, "|", "&");
        }

        return visitChildren(ctx);
    }

    @Override
    public Void visitLogical_test(PythonParser.Logical_testContext ctx) {
        if (mutationType == Mutator.MutatorType.INVERT_NEGS && ctx.NOT() != null) {
            Token notToken = ctx.NOT().getSymbol();
            addMutation(notToken, "not ", "");
            return visitChildren(ctx);
        }

        if (ctx.op == null) {
            return visitChildren(ctx);
        }

        String operator = ctx.op.getText();
        if (mutationType == Mutator.MutatorType.LOGICAL_AND_TO_OR && operator.equals("and")) {
            addMutation(ctx.op, "and", "or");
        } else if (mutationType == Mutator.MutatorType.LOGICAL_OR_TO_AND && operator.equals("or")) {
            addMutation(ctx.op, "or", "and");
        }

        return visitChildren(ctx);
    }

    @Override
    public Void visitComparison(PythonParser.ComparisonContext ctx) {
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (!(child instanceof TerminalNode terminalNode)) {
                continue;
            }

            Token token = terminalNode.getSymbol();
            String operator = token.getText();

            if (mutationType == Mutator.MutatorType.ROR_LT_TO_LE && operator.equals("<")) {
                addMutation(token, "<", "<=");
            } else if (mutationType == Mutator.MutatorType.ROR_LE_TO_LT && operator.equals("<=")) {
                addMutation(token, "<=", "<");
            } else if (mutationType == Mutator.MutatorType.ROR_GT_TO_GE && operator.equals(">")) {
                addMutation(token, ">", ">=");
            } else if (mutationType == Mutator.MutatorType.ROR_GE_TO_GT && operator.equals(">=")) {
                addMutation(token, ">=", ">");
            } else if (mutationType == Mutator.MutatorType.EQ_NEGATE && operator.equals("==")) {
                addMutation(token, "==", "!=");
            } else if (mutationType == Mutator.MutatorType.EQ_NEGATE && operator.equals("!=")) {
                addMutation(token, "!=", "==");
            } else if (mutationType == Mutator.MutatorType.ROR_GT_TO_LT && operator.equals(">")) {
                addMutation(token, ">", "<");
            } else if (mutationType == Mutator.MutatorType.ROR_LT_TO_GT && operator.equals("<")) {
                addMutation(token, "<", ">");
            } else if (mutationType == Mutator.MutatorType.ROR_GTE_TO_LT && operator.equals(">=")) {
                addMutation(token, ">=", "<");
            } else if (mutationType == Mutator.MutatorType.ROR_LTE_TO_GT && operator.equals("<=")) {
                addMutation(token, "<=", ">");
            }
        }

        return visitChildren(ctx);
    }

    private void maybeAddTypeSwapMutation(String annotatedType, Token token) {
        if (mutationType == Mutator.MutatorType.TYPE_INT_TO_FLOAT && annotatedType.equals("int")) {
            addMutation(token, "int", "float");
        } else if (mutationType == Mutator.MutatorType.TYPE_FLOAT_TO_INT && annotatedType.equals("float")) {
            addMutation(token, "float", "int");
        }
    }

    private void addMutation(Token token, String operator, String replacement) {
        String key = token.getLine() + ":" + token.getCharPositionInLine() + ":" + operator + ":" + replacement;
        if (!seenMutations.add(key)) {
            return;
        }

        mutationPoints.add(new MutationPoint(
                sourceFile,
                token.getLine(),
                token.getCharPositionInLine(),
                operator,
                replacement,
                mutationType
        ));
    }

    public List<MutationPoint> getMutationPoints() {
        return mutationPoints;
    }

    public List<Mutation> getMutations() {
        List<Mutation> legacyMutations = new ArrayList<>();
        for (MutationPoint point : mutationPoints) {
            legacyMutations.add(new Mutation(
                    point.getType().name(),
                    point.getLineNumber(),
                    point.getColumnNumber(),
                    point.getOperator(),
                    point.getReplacement(),
                    getSourceLine(point.getLineNumber())
            ));
        }
        return legacyMutations;
    }

    public void clearMutations() {
        mutationPoints.clear();
        seenMutations.clear();
    }

    private String getSourceLine(int lineNumber) {
        String[] lines = sourceCode.split("\n", -1);
        if (lineNumber > 0 && lineNumber <= lines.length) {
            return lines[lineNumber - 1].trim();
        }
        return "";
    }
}
