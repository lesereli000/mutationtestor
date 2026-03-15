#lang racket/base

(require "src/arithmetic.rkt"
         "src/statistics.rkt")

; Example usage of the calculator library
(module+ main
  (displayln "=== Calculator Demo ===")
  (displayln "")

  (displayln "Arithmetic Operations:")
  (displayln (format "5 + 3 = ~a" (add 5 3)))
  (displayln (format "10 - 4 = ~a" (subtract 10 4)))
  (displayln (format "6 * 7 = ~a" (multiply 6 7)))
  (displayln (format "20 / 4 = ~a" (divide 20 4)))
  (displayln (format "9^2 = ~a" (square 9)))
  (displayln "")

  (displayln "Predicates:")
  (displayln (format "Is 4 even? ~a" (is-even? 4)))
  (displayln (format "Is 7 even? ~a" (is-even? 7)))
  (displayln (format "Is 10 positive? ~a" (is-positive? 10)))
  (displayln (format "Is -5 positive? ~a" (is-positive? -5)))
  (displayln "")

  (displayln "Statistics:")
  (define numbers '(10 20 30 40 50))
  (displayln (format "Numbers: ~a" numbers))
  (displayln (format "Sum: ~a" (sum numbers)))
  (displayln (format "Average: ~a" (average numbers)))
  (displayln (format "Maximum: ~a" (maximum numbers)))
  (displayln (format "Minimum: ~a" (minimum numbers))))
