#lang racket

(require rackunit
         rackunit/text-ui
         "../src/add1.rkt"
         "../src/add2.rkt"
         "../src/operators.rkt"
         "../src/typed-functions.rkt")

(define add1-suite
  (test-suite "Test add1"
    (check-equal? 6 (add1 5))
    (check-equal? 0 (add1 -1))
    (check-equal? 789 (add1 788))
    (check-equal? 12 (add1 11))))

(define add2-suite
  (test-suite "Test add2"
    (check-equal? 7 (add2 5))
    (check-equal? 1 (add2 -1))
    (check-equal? 790 (add2 788))
    (check-equal? 13 (add2 11))))

(define operators-suite
  (test-suite "Test operators"
    (check-equal? (add 3 4) 7)
    (check-equal? (add 0 0) 0)
    (check-equal? (subtract 10 3) 7)
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
    (check-equal? (check-floats) '(5 3.14 10))
    (check-equal? (multiply 3 4) 12)
    (check-equal? (multiply 2 5) 10)
    (check-equal? (divide 10 2) 5)
    (check-equal? (divide 9 3) 3)))

(define typed-functions-suite
  (test-suite "Test typed-functions"
    (check-equal? (typed-add 2 3) 6)   ; 2+3+1=6, mutant gives 2-3+1=0 → fail
    (check-equal? (mixed-types 2 3 4 5) 29)))  ; (2*3)+(4*5)+3=29

(define all-tests
  (test-suite "All Tests"
    add1-suite
    add2-suite
    operators-suite
    ;typed-functions-suite
    ))


; run with ' Racket ./project/tests/racket-project.rkt '
(module+ main
  (void
   (run-tests all-tests)))