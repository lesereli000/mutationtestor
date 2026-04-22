// Scheme/Racket Parser Grammar for ANTLR4
// Depends on SchemeLexer.g4.
// Produces a parse tree of S-expressions that SchemeMutationRunner
// walks with a visitor to find mutation points.

parser grammar RacketParser;

options {
    tokenVocab = RacketLexer;
}

// -------------------------------------------------------------------------
// Top-level rule
// A Racket file is a sequence of zero or more S-expressions.
// -------------------------------------------------------------------------

program
    : form* EOF
    ;

// -------------------------------------------------------------------------
// A form is any complete S-expression
// -------------------------------------------------------------------------

form
    : list
    | atom
    | quotedForm
    | typeAnnotation
    ;

// -------------------------------------------------------------------------
// List: ( form* ) or [ form* ]
// The first child of a list is the operator/function in head position.
// e.g. (+ a b), (define (f x) (+ x 1)), (if (> x 0) x (- x))
// -------------------------------------------------------------------------

list
    : LPAREN form* RPAREN       # parenList
    | LBRACKET form* RBRACKET   # bracketList
    ;

// -------------------------------------------------------------------------
// Quoted forms: 'x `x ,x ,@x
// Treated as opaque — we don't scan inside quoted forms for operators
// since they represent data, not code.
// -------------------------------------------------------------------------

quotedForm
    : QUOTE form
    | QUASIQUOTE form
    | UNQUOTE form
    | UNQUOTE_AT form
    ;

// -------------------------------------------------------------------------
// Atoms: any leaf token
// Operators (+, -, >, >=, =, and, or) appear here when in non-head position,
// and also as the first child of a list when in head (operator) position.
// -------------------------------------------------------------------------

atom
    : PLUS
    | MINUS
    | STAR
    | SLASH
    | GT
    | LT
    | GEQ
    | LEQ
    | EQ
    | BOOL
    | NUMBER
    | STRING
    | CHARACTER
    | HASH_PREFIX
    | IDENTIFIER
    | ARROW
    ;

// -------------------------------------------------------------------------
// Type annotation: (: function-name Type)
// In Typed Racket: (: typed-add (-> Integer Integer Integer))
// -------------------------------------------------------------------------

typeAnnotation
    : LPAREN COLON IDENTIFIER typeExpr RPAREN
    ;

typeExpr
    : IDENTIFIER                                    # simpleType
    | LPAREN ARROW typeExpr+ RPAREN                 # functionType
    ;

