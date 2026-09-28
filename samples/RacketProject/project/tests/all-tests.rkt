#lang racket

(require rackunit
         rackunit/text-ui
         "../src/pit-mutations.rkt"
         "../src/add1.rkt"
         "../src/add2.rkt"
         "../src/operators.rkt"
         "../src/typed-functions.rkt")

; -------------------------------------------------------------------------
; pit-mutations.rkt
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

(define increment-suite
  (test-suite "Increments: add1/sub1 swap"
    (check-equal? (increment-x 5)  6)
    (check-equal? (increment-x 0)  1)
    (check-equal? (increment-x -1) 0)
    (check-equal? (decrement-x 5)  4)
    (check-equal? (decrement-x 1)  0)
    (check-equal? (decrement-x 0) -1)))

(define ineg-suite
  (test-suite "INEG: unary minus removed"
    (check-equal? (negate-x  5)  -5)
    (check-equal? (negate-x -3)   3)
    (check-equal? (negate-x  0)   0)))

(define rvm-suite
  (test-suite "RVM: return value mutations"
    (check-equal? (always-true)  #t)
    (check-equal? (always-false) #f)
    (check-equal? (return-zero)  0)
    (check-equal? (return-nonzero) 42)
    (check-equal? (get-pair 1 2) '(1 . 2))
    (check-equal? (get-pair 0 0) '(0 . 0))
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
; add1.rkt / add2.rkt
; -------------------------------------------------------------------------
(define add1-suite
  (test-suite "Test add1"
    (check-equal? (add1 5)   6)
    (check-equal? (add1 0)   1)
    (check-equal? (add1 -1)  0)
    (check-equal? (add1 788) 789)))

(define add2-suite
  (test-suite "Test add2"
    (check-equal? (add2 5)   7)
    (check-equal? (add2 0)   2)
    (check-equal? (add2 -1)  1)
    (check-equal? (add2 788) 790)))

; -------------------------------------------------------------------------
; operators.rkt
; -------------------------------------------------------------------------
(define operators-suite
  (test-suite "Test operators"
    (check-equal? (add 3 4) 7)
    (check-equal? (add 0 0) 0)
    (check-equal? (subtract 10 3) 7)
    (check-equal? (subtract 5 5) 0)
    (check-equal? (compare-gt 5 3) "greater")
    (check-equal? (compare-gt 3 5) "not-greater")
    (check-equal? (compare-gt 5 5) "not-greater")
    (check-equal? (compare-ge 5 5) "greater-or-equal")
    (check-equal? (compare-ge 3 5) "less")
    (check-equal? (compare-lt 3 5) "less")
    (check-equal? (compare-lt 5 3) "not-less")
    (check-equal? (compare-lt 5 5) "not-less")
    (check-equal? (compare-le 5 5) "less-or-equal")
    (check-equal? (compare-le 5 3) "greater")
    (check-equal? (check-equal 5 5) "equal")
    (check-equal? (check-equal 5 3) "not-equal")
    (check-equal? (check-logic 1 2) "both-positive")
    (check-equal? (check-logic 0 2) "one-positive")
    (check-equal? (check-logic 2 0) "one-positive")
    (check-equal? (check-logic 0 0) "neither-positive")
    (check-equal? (check-logic -1 2) "one-positive")
    (check-equal? (check-logic -1 -2) "neither-positive")
    ; check-floats: kills RVM_NONZERO_TO_ZERO on literal 5
    (check-equal? (check-floats) '(5 3.14 10))
    (check-equal? (multiply 3 4) 12)
    (check-equal? (multiply 2 5) 10)
    (check-equal? (divide 10 2) 5)
    (check-equal? (divide 9 3) 3)
    ; clamp-to-zero / under-limit?
    (check-equal? (clamp-to-zero 0)  0)
    (check-equal? (clamp-to-zero -1) 0)
    (check-equal? (clamp-to-zero 5)  5)
    (check-equal? (under-limit? 3 5) #t)
    (check-equal? (under-limit? 5 5) #t)
    (check-equal? (under-limit? 6 5) #f)
    ; greet: kills VML mutation on (display name)
    (test-case "greet displays its argument"
      (define out (open-output-string))
      (parameterize ([current-output-port out])
        (greet "Alice"))
      (check-false (string=? (get-output-string out) "")
                   "display should have written something"))
    (check-equal? (greet "Alice") 5)))

; -------------------------------------------------------------------------
; typed-functions.rkt
; -------------------------------------------------------------------------
(define typed-functions-suite
  (test-suite "Test typed-functions"
    (check-equal? (typed-add 2 3) 6)
    (check-equal? (typed-add 0 0) 1)
    (check-equal? (typed-multiply 2 3) 12)
    (check-equal? (typed-multiply 1 1) 2)
    (check-equal? (typed-divide 10 2) 5)
    (check-equal? (typed-divide 9 3) 3)
    (check-equal? (check-types 1 0) "positive")
    (check-equal? (check-types -1 0) "non-positive")
    (check-equal? (mixed-types 2 3 4 5) 29)
    (check-equal? (mixed-types 1 1 1 1) 5)))

(define all-tests
  (test-suite "All Tests"
    vml-suite
    increment-suite
    ineg-suite
    rvm-suite
    remove-conditionals-suite
    empty-returns-suite
    add1-suite
    add2-suite
    operators-suite
    typed-functions-suite))

(module+ main
  (void (run-tests all-tests)))