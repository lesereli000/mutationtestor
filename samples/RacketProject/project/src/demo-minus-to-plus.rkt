#lang racket

(define (sub a b)
  (- a b)) ; mutant: (+ a b)

(define (chain a b c)
  (- (- a b) c)) ; mutant: (+ (+ a b) c)

(define (array-minus v i)
  (- (vector-ref v i) 1)) ; mutant: (+ (vector-ref v i) 1)

(define (unary-minus a)
  (- a)) ; mutant: (+ a)


(displayln (sub 5 3))                       ; 2 -> 8
(displayln (chain 10 3 2))                  ; 5 -> 15
(displayln (array-minus #(10 20 30) 1))     ; 19 -> 21
(displayln (unary-minus 7))                 ; -7 -> 7

(displayln (- 10 2 3))                      ; 5 -> 15
(displayln (- (- 10 2) 3))                  ; 5 -> 15

; ===== tests: '-' inside comments should NOT be mutated =====
; (- 10 2 3)
; (- (- 10 2) 3)
; a-b-c
