grammar Expr;

@header {
package org.github.eval.parser;
}

expression         : comparison EOF ;
comparison         : concatenation (comparisonOperator concatenation)* ;
comparisonOperator : '=' | '<>' | '<=' | '>=' | '<' | '>' ;
concatenation      : additive ('&' additive)* ;
additive           : multiplicative (additiveOperator multiplicative)* ;
additiveOperator   : '+' | '-' ;
multiplicative     : unary (multiplicativeOperator unary)* ;
multiplicativeOperator : '*' | '/' ;
unary              : sign=('+'|'-') unary | primary ;
primary            : NUMBER
                   | STRING
                   | booleanLiteral
                   | functionCall
                   | variable
                   | '(' comparison ')'
                   ;
booleanLiteral     : TRUE | FALSE ;
functionCall       : IDENTIFIER '(' (comparison (',' comparison)*)? ')' ;
variable           : IDENTIFIER ;

TRUE       : [Tt][Rr][Uu][Ee] ;
FALSE      : [Ff][Aa][Ll][Ss][Ee] ;
NUMBER     : [0-9]+ ('.' [0-9]*)? ([Ee] [+-]? [0-9]+)?
           | '.' [0-9]+ ([Ee] [+-]? [0-9]+)? ;
STRING     : '"' ('""' | ~["])* '"' ;
IDENTIFIER : [A-Za-z_][A-Za-z0-9_.]* ;
WS         : [ \t\r\n]+ -> skip ;
