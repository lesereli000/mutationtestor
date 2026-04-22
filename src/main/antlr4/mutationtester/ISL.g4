/* Based on documentation provided at https://docs.racket-lang.org/htdp-langs/intermediate-lam.html */
/* Adapted for MutationTester: targets #lang htdp/isl and #lang racket subsets */

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
    : '(' 'define' '(' name NAME+ ')' expr ')'
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
    | '(' 'lambda' '(' NAME* ')' expr ')'
    | '(' '\u03BB' '(' NAME* ')' expr ')'
    | '(' 'local' '[' definition* ']' expr ')'
    | '(' 'letrec' '(' ('[' name expr ']')* ')' expr ')'
    | '(' 'shared' '(' ('[' name expr ']')* ')' expr ')'
    | '(' 'let' '(' ('[' name expr ']')* ')' expr ')'
    | '(' 'let*' '(' ('[' name expr ']')* ')' expr ')'
    | '(' 'recur' name '(' ('[' name expr ']')* ')' expr ')'
    | '(' 'cond' ('[' expr expr ']')+ ')'
    | '(' 'cond' ('[' expr expr ']')* '[' 'else' expr ']' ')'
    | '(' 'if' expr expr expr ')'
    | '(' 'and' expr expr+ ')'
    | '(' 'or' expr expr+ ')'
    | '(' 'time' expr ')'
    | '(' expr expr* ')'
    | name
    | '\'' quoted
    | '\'' quasiQuoted
    | '\'' '()'
    | NUMBER
    | BOOLEAN
    | STRING
    | CHARACTER
    ;

quoted
    : NAME
    | STRING
    | CHARACTER
    | '(' quoted* ')'
    | '\'' quoted
    | ',' quoted
    | ',@' quoted
    ;

quasiQuoted
    : NAME
    | NUMBER
    | STRING
    | CHARACTER
    | '(' quasiQuoted* ')'
    | '\'' quasiQuoted
    | ',' expr
    | ',@' expr
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
// Lexer rules
// -------------------------------------------------------------------------

SYMBOL
    : '\'' NAME
    ;

// Operators defined before NAME so they tokenize separately when standalone
PLUS    : '+' ;
MINUS   : '-' ;
STAR    : '*' ;
SLASH   : '/' ;
GT      : '>'  ;
LT      : '<'  ;
GEQ     : '>=' ;
LEQ     : '<=' ;
EQ      : '='  ;

NAME
    : ([$%&!*+\-\\^_~] | [--:<-Za-z] | [/=><!?] )+ 
    ;

NUMBER
    : INT
    | INT '.' [0-9]* [1-9]
    | INT '/' INT
    | '-' INT
    | '-' INT '.' [0-9]* [1-9]
    ;

INT
    : [1-9] [0-9]*
    | '0'
    ;

BOOLEAN
    : '#true'
    | '#T'
    | '#t'
    | '#false'
    | '#F'
    | '#f'
    ;

STRING
    : '"' (~["\\\r\n] | '\\' .)* '"'
    ;

CHARACTER
    : '#\\' [A-Za-z0-9]
    | '#\\' 'space'
    | '#\\' 'newline'
    | '#\\' 'tab'
    ;

LANG
    : '#lang' ~[\r\n]* '\r'? '\n' -> channel(HIDDEN)
    ;

BLOCK_COMMENT
    : '#|' .*? '|#' -> channel(HIDDEN)
    ;

COMMENT
    : ';' ~[\r\n]* '\r'? '\n'? -> channel(HIDDEN)
    ;

WS
    : [ \r\t\u000C\n]+ -> channel(HIDDEN)
    ;
