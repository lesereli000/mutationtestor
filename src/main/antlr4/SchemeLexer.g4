// Scheme/Racket Lexer Grammar for ANTLR4
// Adapted for Racket mutation scanning.
// Handles the subset of Racket syntax needed to identify
// arithmetic, relational, and logical operator mutation points.

lexer grammar SchemeLexer;

// -------------------------------------------------------------------------
// Whitespace and comments — skipped, never reach the parser
// -------------------------------------------------------------------------

WHITESPACE
    : [ \t\r\n]+
    -> skip
    ;

// Line comment: ; to end of line
LINE_COMMENT
    : ';' ~[\r\n]*
    -> skip
    ;

// Block comment: #| ... |#
BLOCK_COMMENT
    : '#|' .*? '|#'
    -> skip
    ;

// #lang directive line — skip the entire line
HASH_LANG
    : '#lang' ~[\r\n]*
    -> skip
    ;

// -------------------------------------------------------------------------
// Delimiters
// -------------------------------------------------------------------------

LPAREN  : '(' ;
RPAREN  : ')' ;
LBRACKET: '[' ;
RBRACKET: ']' ;

// -------------------------------------------------------------------------
// Operators — defined before IDENTIFIER so they match first
// -------------------------------------------------------------------------

// Relational — >= and <= must come before > and < to avoid prefix match
GEQ     : '>=' ;
LEQ     : '<=' ;
GT      : '>'  ;
LT      : '<'  ;
EQ      : '='  ;

// Arithmetic
PLUS    : '+'  ;
MINUS   : '-'  ;
STAR    : '*'  ;
SLASH   : '/'  ;

// -------------------------------------------------------------------------
// Literals
// -------------------------------------------------------------------------

// Boolean literals
BOOL
    : '#t' | '#f' | '#true' | '#false'
    ;

// Character literals: #\a, #\space, #\newline, etc.
CHARACTER
    : '#\\' ( ~[ \t\r\n] | 'space' | 'newline' | 'tab' | 'return' | 'null' | 'nul' | 'backspace' | 'delete' | 'escape' | 'altmode' | 'rubout' )
    ;

// Number: integer or decimal, optional sign
NUMBER
    : [+-]? [0-9]+ ('.' [0-9]*)?
    | [+-]? '.' [0-9]+
    ;

// String literal — swallowed whole so operators inside are never tokenized
STRING
    : '"' ( '\\' . | ~[\\"] )* '"'
    ;

// -------------------------------------------------------------------------
// Type annotations (for Typed Racket)
// -------------------------------------------------------------------------

// Type annotation operator
COLON   : ':' ;

// Arrow for function types
ARROW   : '->' ;

// -------------------------------------------------------------------------
// Identifiers
// Racket identifiers can contain letters, digits, and: - _ ? ! > < = + * / .
// Read greedily — list->vector, zero?, set!, my-var are single tokens.
// Must come AFTER operator tokens so +, -, >, >= etc. match their own rules
// when they appear standalone (i.e. as the head of a list).
// -------------------------------------------------------------------------

IDENTIFIER
    : ID_START ID_CONTINUE*
    ;

fragment ID_START
    : [a-zA-Z_]
    | [\u0080-\uFFFF]   // unicode identifiers
    ;

fragment ID_CONTINUE
    : [a-zA-Z0-9_\-?!.+*/<>=]
    | [\u0080-\uFFFF]
    ;

// Quote shorthand: 'x `x ,x ,@x
QUOTE       : '\'' ;
QUASIQUOTE  : '`'  ;
UNQUOTE_AT  : ',@' ;
UNQUOTE     : ','  ;

// Hash-prefixed reader extensions (#;datum comment, #( vector, etc.)
HASH_PREFIX
    : '#' ~[ \t\r\n()[\]";#]*
    ;
