#lang racket

(require rackunit
         rackunit/text-ui
         "../src/arithmetic.rkt")

(provide arithmetic-test-suite)

(define arithmetic-test-suite
  (test-suite "Arithmetic Operations Tests"

    (test-case "Addition tests"
      (check-equal? (add 2 3) 5)
      (check-equal? (add -1 1) 0)
      (check-equal? (add 0 0) 0)
      (check-equal? (add 100 200) 300))

    (test-case "Subtraction tests"
      (check-equal? (subtract 5 3) 2)
      (check-equal? (subtract 1 1) 0)
      (check-equal? (subtract 0 5) -5)
      (check-equal? (subtract -3 -5) 2))

    (test-case "Multiplication tests"
      (check-equal? (multiply 3 4) 12)
      (check-equal? (multiply 0 10) 0)
      (check-equal? (multiply -2 3) -6)
      (check-equal? (multiply -4 -5) 20))

    (test-case "Division tests"
      (check-equal? (divide 10 2) 5)
      (check-equal? (divide 7 2) 7/2)
      (check-equal? (divide -10 5) -2)
      (check-exn exn:fail? (lambda () (divide 5 0))))

    (test-case "Square tests"
      (check-equal? (square 5) 25)
      (check-equal? (square 0) 0)
      (check-equal? (square -3) 9)
      (check-equal? (square 10) 100))

    (test-case "Even predicate tests"
      (check-true (is-even? 4))
      (check-true (is-even? 0))
      (check-true (is-even? -2))
      (check-false (is-even? 3))
      (check-false (is-even? -1)))

    (test-case "Positive predicate tests"
      (check-true (is-positive? 1))
      (check-true (is-positive? 100))
      (check-false (is-positive? 0))
      (check-false (is-positive? -1))
      (check-false (is-positive? -100)))))

; Run tests when this file is executed directly
(module+ main
  (void (run-tests arithmetic-test-suite)))
