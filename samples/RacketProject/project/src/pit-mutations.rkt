#lang racket

(provide
  log-and-count
  increment-x decrement-x
  negate-x
  always-true always-false
  return-zero return-nonzero
  get-pair get-id
  describe-zero sign
  greeting vowels pair-list sum-of-squares parity origin point? point-x point-y empty-table)

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

; REMOVE_CONDITIONALS_EQUAL_IF / _ELSE: (= n 0) -> #t / #f
(define (describe-zero n)
  (if (= n 0) "zero" "non-zero"))

; REMOVE_CONDITIONALS_ORDER_IF / _ELSE: (< n 0) and (> n 0) -> #t / #f
(define (sign n)
  (cond [(< n 0) "negative"]
        [(> n 0) "positive"]
        [else "zero"]))

; EMPTY_RETURNS: string-building call -> ""
(define (greeting name)
  (string-append "Hello, " name))

; EMPTY_RETURNS: quoted list literal -> '()
(define (vowels)
  '(a e i o u))

; EMPTY_RETURNS: list-building call -> '()
(define (pair-list x y)
  (list x y))

; NON_VOID_METHOD_CALLS: numeric call result -> 0
(define (sum-of-squares a b)
  (+ (sqr a) (sqr b)))

; NON_VOID_METHOD_CALLS: predicate call result -> #f
(define (parity n)
  (if (even? n) "even" "odd"))

; CONSTRUCTOR_CALLS: struct constructor call -> '()
(define-struct point (x y))

(define (origin)
  (make-point 0 0))

; CONSTRUCTOR_CALLS: built-in make- constructor -> '()
(define (empty-table)
  (make-hash))
