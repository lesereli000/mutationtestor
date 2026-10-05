#lang racket

(require rackunit
         rackunit/text-ui
         "../src/pit-mutations.rkt")

; -------------------------------------------------------------------------
; A. VML_VOID_METHOD_CALL
;    Mutant removes (display items), leaving only (length items).
;    Kill it by asserting that output was actually produced.
; -------------------------------------------------------------------------
(define vml-suite
  (test-suite "VML: void method call removed"
    (test-case "log-and-count displays its argument"
      (define out (open-output-string))
      (parameterize ([current-output-port out])
        (log-and-count '(a b c)))
      (check-false (string=? (get-output-string out) "")
                   "display should have written something to stdout"))
    (test-case "log-and-count return value is unaffected"
      (check-equal? (log-and-count '(1 2 3)) 3))))

; -------------------------------------------------------------------------
; B. Increments
;    AOR_INCREMENT_TO_DECREMENT : add1 -> sub1  (5+1=6, mutant gives 4)
;    AOR_DECREMENT_TO_INCREMENT : sub1 -> add1  (5-1=4, mutant gives 6)
; -------------------------------------------------------------------------
(define increment-suite
  (test-suite "Increments: add1/sub1 swap"
    (check-equal? (increment-x 5)  6)
    (check-equal? (increment-x 0)  1)
    (check-equal? (increment-x -1) 0)
    (check-equal? (decrement-x 5)  4)
    (check-equal? (decrement-x 1)  0)
    (check-equal? (decrement-x 0) -1)))

; -------------------------------------------------------------------------
; C. Invert Negatives
;    INEG_INVERT_NEGATIVE : (- x) -> x  (negate-x 5 = -5, mutant gives 5)
; -------------------------------------------------------------------------
(define ineg-suite
  (test-suite "INEG: unary minus removed"
    (check-equal? (negate-x  5)  -5)
    (check-equal? (negate-x -3)   3)
    (check-equal? (negate-x  0)   0)))

; -------------------------------------------------------------------------
; D. Return Values
; -------------------------------------------------------------------------
(define rvm-suite
  (test-suite "RVM: return value mutations"

    ; RVM_TRUE_TO_FALSE : #t -> #f
    (check-equal? (always-true)  #t)

    ; RVM_FALSE_TO_TRUE : #f -> #t
    (check-equal? (always-false) #f)

    ; RVM_ZERO_TO_ONE : 0 -> 1
    (check-equal? (return-zero)  0)

    ; RVM_NONZERO_TO_ZERO / RVM_PRIMITIVE_TO_ZERO : 42 -> 0
    (check-equal? (return-nonzero) 42)

    ; RVM_OBJECT_TO_NULL : (cons x y) -> '()
    (check-equal? (get-pair 1 2) '(1 . 2))
    (check-equal? (get-pair 0 0) '(0 . 0))

    ; RVM_OBJECT_TO_NULL : x -> '()
    (check-equal? (get-id 42)  42)
    (check-equal? (get-id "hi") "hi")))

; -------------------------------------------------------------------------
; E. Remove Conditionals
;    REMOVE_CONDITIONALS_EQUAL_IF/ELSE : (= n 0) -> #t / #f
;    REMOVE_CONDITIONALS_ORDER_IF/ELSE : (< n 0), (> n 0) -> #t / #f
; -------------------------------------------------------------------------
(define remove-conditionals-suite
  (test-suite "Remove conditionals: condition replaced with #t/#f"
    (check-equal? (describe-zero 0)  "zero")
    (check-equal? (describe-zero 1)  "non-zero")
    (check-equal? (sign -5) "negative")
    (check-equal? (sign  5) "positive")
    (check-equal? (sign  0) "zero")))

; -------------------------------------------------------------------------
; F. Empty Returns
;    EMPTY_RETURNS : returned string -> "", returned list -> '()
; -------------------------------------------------------------------------
(define empty-returns-suite
  (test-suite "Empty returns: string/list result replaced with empty value"
    (check-equal? (greeting "Ada") "Hello, Ada")
    (check-equal? (vowels) '(a e i o u))
    (check-equal? (pair-list 1 2) '(1 2))))

; -------------------------------------------------------------------------
; G. Non-void Method Calls
;    NON_VOID_METHOD_CALLS : (sqr a) -> 0, (even? n) -> #f
; -------------------------------------------------------------------------
(define non-void-calls-suite
  (test-suite "Non-void calls: call result replaced with default value"
    (check-equal? (sum-of-squares 3 4) 25)
    (check-equal? (parity 2) "even")
    (check-equal? (parity 3) "odd")))

; -------------------------------------------------------------------------
; H. Constructor Calls
;    CONSTRUCTOR_CALLS : (make-point 0 0) -> '(), (make-hash) -> '()
; -------------------------------------------------------------------------
(define constructor-calls-suite
  (test-suite "Constructor calls: constructed value replaced with '()"
    (check-true (point? (origin)))
    (check-equal? (point-x (origin)) 0)
    (check-equal? (point-y (origin)) 0)
    (check-true (hash? (empty-table)))))

; -------------------------------------------------------------------------
; I. Remove Logical Negation
;    REMOVE_LOGICAL_NEGATION : (not (empty? items)) -> (empty? items)
; -------------------------------------------------------------------------
(define remove-negation-suite
  (test-suite "Remove logical negation: (not p) replaced with p"
    (check-true (non-empty? '(1 2)))
    (check-false (non-empty? '()))))

(define all-tests
  (test-suite "PIT Mutation Tests"
    vml-suite
    increment-suite
    ineg-suite
    rvm-suite
    remove-conditionals-suite
    empty-returns-suite
    non-void-calls-suite
    constructor-calls-suite
    remove-negation-suite))

(module+ main
  (void (run-tests all-tests)))