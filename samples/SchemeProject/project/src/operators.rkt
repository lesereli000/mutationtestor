#lang racket

(provide add subtract compare-gt compare-ge check-equal check-logic check-floats)

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
