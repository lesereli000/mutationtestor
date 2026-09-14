#lang racket

(provide
  log-and-count
  increment-x decrement-x
  negate-x
  always-true always-false
  return-zero return-nonzero
  get-pair get-id)

; VML_VOID_METHOD_CALL: void side-effecting call in begin block
(define (log-and-count items)
  (begin
    (display items)
    (length items)))

; AOR_INCREMENT_TO_DECREMENT: add1 -> sub1
(define (increment-x x)
  (add1 x))

; AOR_DECREMENT_TO_INCREMENT: sub1 -> add1
(define (decrement-x x)
  (sub1 x))

; INEG_INVERT_NEGATIVE: (- x) -> x  (unary minus, single argument)
(define (negate-x x)
  (- x))

; RVM_TRUE_TO_FALSE: #t -> #f
(define (always-true)
  #t)

; RVM_FALSE_TO_TRUE: #f -> #t
(define (always-false)
  #f)

; RVM_ZERO_TO_ONE: 0 -> 1
(define (return-zero)
  0)

; RVM_NONZERO_TO_ZERO and RVM_PRIMITIVE_TO_ZERO: 42 -> 0
(define (return-nonzero)
  42)

; RVM_OBJECT_TO_NULL: complex expression -> '()
(define (get-pair x y)
  (cons x y))

; RVM_OBJECT_TO_NULL: identifier -> '()
(define (get-id x)
  x)