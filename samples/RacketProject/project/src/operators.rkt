#lang racket

(provide add subtract compare-gt compare-ge check-equal check-logic check-floats compare-lt compare-le multiply divide)

; AOR: + and -
(define (add x y)
  (+ x y))

(define (subtract x y)
  (- x y))

; ROR: > and >=
(define (compare-gt x y)
  (if (> x y)
      "greater"
      "not-greater"))

(define (compare-ge x y)
  (if (>= x y)
      "greater-or-equal"
      "less"))

; ROR: < and <=
(define (compare-lt x y)
  (if (< x y)
      "less"
      "not-less"))

(define (compare-le x y)
  (if (<= x y)
      "less-or-equal"
      "greater"))


; EQ_NEGATE: =
(define (check-equal x y)
  (if (= x y)
      "equal"
      "not-equal"))

; LOGICAL: and / or
(define (check-logic x y)
  (if (and (> x 0) (> y 0))
      "both-positive"
      (if (or (> x 0) (> y 0))
          "one-positive"
          "neither-positive")))

; NLT: integer and float literals
(define (check-floats)
  (define a 5)
  (define b 3.14)
  (define c 10)
  (list a b c))

; AOR: * and /
(define (multiply x y)
  (* x y))

(define (divide x y)
  (/ x y))

; AOR_MODULO_TO_MULTIPLY: modulo and remainder
(define (mod-test x y)
  (modulo x y))

; BOR: bitwise operators
(define (bitwise-test x y)
  (bitwise-and x y))

(define (bitwise-or-test x y)
  (bitwise-or x y))








