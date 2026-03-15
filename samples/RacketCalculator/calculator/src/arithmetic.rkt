#lang racket/base

(provide add subtract multiply divide square is-even? is-positive?)

; Basic arithmetic operations
(define (add x y)
  (+ x y))

(define (subtract x y)
  (- x y))

(define (multiply x y)
  (* x y))

(define (divide x y)
  (if (= y 0)
      (error "Division by zero")
      (/ x y)))

(define (square x)
  (* x x))

; Predicates
(define (is-even? x)
  (= (modulo x 2) 0))

(define (is-positive? x)
  (> x 0))
