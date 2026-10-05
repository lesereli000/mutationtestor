#lang racket

(require rackunit)
(require "../src/prototypeSample.rkt")

(test-case "Main function coverage"
  (main)
  (check-true (> counter 0)))

(test-case "Math and Logic"
  (check-equal? (math-ops 10 5) '(15 5 50 2 0))
  (check-equal? (relational-ops 10 5) '(#f #t #f #t #f #f #f))
  (check-equal? (logical-ops 1 2) '(#t #t #f))
  (check-equal? (bitwise-ops 6 3) '(2 7 5 -7 12 3)))

(test-case "Conditionals"
  (check-equal? (conditional-check 1) "positive")
  (check-equal? (conditional-check -1) "negative")
  (check-equal? (conditional-check 0) "zero")
  (check-equal? (ternary-check 10 5) 10))

(test-case "Specifics"
  (check-equal? (sum-list '(1 2 3)) 6)
  (check-equal? (process-list '(1 2 3)) '((2 4 6) 6))
  (define h (make-hash))
  (check-equal? (quack h) "Not a duck"))
