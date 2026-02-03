#lang racket

(require rackunit
         rackunit/text-ui
         "../src/add1.rkt"
         "../src/add2.rkt")

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

(define all-tests
  (test-suite "All Tests"
    add1-suite
    add2-suite))


; run with ' Racket .\samples\RacketProject\project\tests\racket-project.rkt '
(module+ main
  (void
   (run-tests all-tests)))