/* Based on documentation provided at https://docs.racket-lang.org/htdp-langs/intermediate-lam.html */
/* Adapted for MutationTester: supports #lang racket and #lang htdp/isl files */

// $antlr-format alignTrailingComments true, columnLimit 150, minEmptyLines 1, maxEmptyLinesToKeep 1, reflowComments false, useTab false
// $antlr-format allowShortRulesOnASingleLine false, allowShortBlocksOnASingleLine true, alignSemicolons hanging, alignColons hanging

grammar ISL;

program
    : defOrExpr* EOF
    ;

defOrExpr
    : definition
    | expr
    | testCase
    | libraryRequire
    ;

definition
    : '(' 'define' '(' name NAME* ')' (definition | expr)+ ')'  // #lang racket allows internal defines
    | '(' 'define' name expr ')'
    | '(' 'define-struct' name '(' name* ')' ')'
    | '(' 'provide' name+ ')'
    | '(' 'module+' name expr+ ')'
    ;

expr
    : '(' 'begin' expr+ ')'
    | '(' 'begin0' expr+ ')'
    | '(' 'set!' NAME expr ')'
    | '(' 'delay' expr ')'
    // #lang racket allows several forms (and internal defines) in lambda and let bodies
    | '(' 'lambda' '(' NAME* ')' (definition | expr)+ ')'
    | '(' '\u03BB' '(' NAME* ')' (definition | expr)+ ')'
    | '(' 'local' '[' definition* ']' expr ')'
    | '(' 'letrec' '(' ('[' name expr ']')* ')' (definition | expr)+ ')'
    | '(' 'shared' '(' ('[' name expr ']')* ')' expr ')'
    | '(' 'let' '(' ('[' name expr ']')* ')' (definition | expr)+ ')'
    | '(' 'let*' '(' ('[' name expr ']')* ')' (definition | expr)+ ')'
    | '(' 'recur' name '(' ('[' name expr ']')* ')' expr ')'
    | '(' 'cond' ('[' expr expr ']')+ ')'
    | '(' 'cond' ('[' expr expr ']')* '[' 'else' expr ']' ')'
    | '(' 'if' expr expr expr ')'
    | '(' 'and' expr expr+ ')'
    | '(' 'or' expr expr+ ')'
    | '(' 'time' expr ')'
    | '(' expr expr* ')'
    | PLUS | MINUS | STAR | SLASH | GT | LT | GEQ | LEQ | EQ
    | name
    | '\'' quoted
    | '\'' '()'
    | NUMBER
    | BOOLEAN
    | STRING
    | CHARACTER
    ;

quoted
    : NAME
    | SYMBOL
    | STRING
    | CHARACTER
    | NUMBER
    | BOOLEAN
    | PLUS | MINUS | STAR | SLASH | GT | LT | GEQ | LEQ | EQ  // '(+ -) quotes the symbols
    | '(' (quoted | '()')* ')'  // the lexer reads a nested () as one '()' token
    | '\'' quoted
    | ',' quoted
    | ',@' quoted
    ;

testCase
    : '(' 'check-expect' expr expr ')'
    | '(' 'check-random' expr expr ')'
    | '(' 'check-within' expr expr expr ')'
    | '(' 'check-member-of' expr expr+ ')'
    | '(' 'check-satisfied' expr name ')'
    | '(' 'check-error' expr expr? ')'
    ;

libraryRequire
    : '(' 'require' STRING ')'
    | '(' 'require' name ')'
    | '(' 'require' '(' name STRING ('(' STRING+ ')')? ')' ')'
    | '(' 'require' '(' name STRING pkg ')' ')'
    ;

pkg
    : '(' STRING STRING NUMBER NUMBER ')'
    ;

name
    : SYMBOL
    | NAME
    ;

// -------------------------------------------------------------------------
// Lexer rules — ordering matters: more specific tokens before NAME
// -------------------------------------------------------------------------

// Operators — must come BEFORE NAME so they match as standalone tokens
// Multi-char operators before single-char to avoid prefix matching
GEQ     : '>=' ;
LEQ     : '<=' ;
GT      : '>'  ;
LT      : '<'  ;
EQ      : '='  ;
PLUS    : '+'  ;
MINUS   : '-'  ;
STAR    : '*'  ;
SLASH   : '/'  ;

// Boolean literals — before NAME so #true/#false don't fall through
BOOLEAN
    : '#true'
    | '#T'
    | '#t'
    | '#false'
    | '#F'
    | '#f'
    ;

// A symbol is a quote followed by a name
SYMBOL
    : '\'' NAME
    ;

// NAME: identifiers that do NOT start with operator characters
// Racket identifiers must START with a letter, _, $, %, &, !, ~, or ^, so
// operators (+, -, *, /, >, <, >=, <=, =) stay separate tokens. After the
// first character they may contain operator characters too: string=?,
// string->number, char<?, list*.
NAME
    : NAME_START NAME_CONTINUE*
    ;

fragment NAME_START
    : [a-zA-Z_$%&!~^]
    ;

fragment NAME_CONTINUE
    : [a-zA-Z0-9_\-?!.~^=<>*/+:%&$]
    ;

// Numbers — integers, decimals, rationals, negatives
NUMBER
    : '#' [xX] [0-9a-fA-F]+     // #xFFFFFFFF
    | '#' [bB] [01]+
    | '#' [oO] [0-7]+
    | SIGN? INT
    | SIGN? INT '.' [0-9]+
    | SIGN? INT '/' INT
    ;

fragment SIGN : [+\-] ;

fragment INT
    : [0-9]+
    ;

// String literal — swallowed whole so operators inside are never tokenized
STRING
    : '"' (~["\\\r\n] | '\\' .)* '"'
    ;

// Character literals: #\a, #\space, #\newline etc.
CHARACTER
    : '#\\' ( 'space' | 'newline' | 'tab' | 'return' | 'nul' | 'null'
            | 'backspace' | 'delete' | 'escape' | 'altmode' | 'rubout'
            | [A-Za-z0-9] )
    ;

// #lang line — skip entirely
LANG
    : '#lang' ~[\r\n]* '\r'? '\n' -> channel(HIDDEN)
    ;

// Block comment: #| ... |#
BLOCK_COMMENT
    : '#|' .*? '|#' -> channel(HIDDEN)
    ;

// Line comment: ; to end of line
COMMENT
    : ';' ~[\r\n]* '\r'? '\n'? -> channel(HIDDEN)
    ;

// Whitespace
WS
    : [ \r\t\u000C\n]+ -> channel(HIDDEN)
    ;
