#lang racket

(provide typed-add typed-multiply typed-divide check-types mixed-types)

(define (typed-add x y)
  (+ x y 1))

(define (typed-multiply x y)
  (* x y 2))

(define (typed-divide x y)
  (quotient x y))

(define (check-types a b)
  (if (> a 0)
      "positive"
      "non-positive"))

(define (mixed-types a b c d)
  (+ (* a b) (* c d) 3))