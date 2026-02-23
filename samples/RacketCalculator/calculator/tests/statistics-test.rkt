#lang racket

(require rackunit
         rackunit/text-ui
         "../src/statistics.rkt")

(provide statistics-test-suite)

(define statistics-test-suite
  (test-suite "Statistics Operations Tests"

    (test-case "Sum tests"
      (check-equal? (sum '(1 2 3 4 5)) 15)
      (check-equal? (sum '(10 20 30)) 60)
      (check-equal? (sum '(0)) 0)
      (check-equal? (sum '()) 0)
      (check-equal? (sum '(-1 -2 -3)) -6))

    (test-case "Average tests"
      (check-equal? (average '(10 20 30)) 20)
      (check-equal? (average '(1 2 3 4 5)) 3)
      (check-equal? (average '(100)) 100)
      (check-equal? (average '()) 0)
      (check-equal? (average '(5 5 5 5)) 5))

    (test-case "Maximum tests"
      (check-equal? (maximum '(1 2 3 4 5)) 5)
      (check-equal? (maximum '(10)) 10)
      (check-equal? (maximum '(-5 -2 -10)) -2)
      (check-equal? (maximum '(100 50 75 200 25)) 200)
      (check-exn exn:fail? (lambda () (maximum '()))))

    (test-case "Minimum tests"
      (check-equal? (minimum '(1 2 3 4 5)) 1)
      (check-equal? (minimum '(10)) 10)
      (check-equal? (minimum '(-5 -2 -10)) -10)
      (check-equal? (minimum '(100 50 75 200 25)) 25)
      (check-exn exn:fail? (lambda () (minimum '()))))))

; Run tests when this file is executed directly
(module+ main
  (void (run-tests statistics-test-suite)))
