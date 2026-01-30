#lang racket

; Basic addition function
(define (add a b) 
  (+ a b)) ; mutant: (- a b)

; Chained addition
(define (chain a b c) 
  (+ (+ a b) c)) ; mutant: (- (- a b) c)

; String with + should not mutate
(displayln "String has +: 2+3")

; line comment with + should stay: a + b

#|
  block comment with + should stay: a + b
|#

(define (GTtoGE)
  (define a 5)
  (define equal a)
  (if (> a equal)
      (displayln "GT to GE Passes")
      (displayln "GT to GE Fails")))

(define (GEtoGT)
  (define a 5)
  (define equal a)
  (if (>= a equal)
      (displayln "GE to GT Fails")
      (displayln "GE to GT Passes")))

; Test outputs
(displayln (add 2 3))           ; 5 -> -1 when mutated
(displayln (chain 1 2 3))       ; 6 -> -4 when mutated
(displayln (+ 1 2 3))           ; 6 -> -4 when mutated

(GTtoGE)
(GEtoGT)