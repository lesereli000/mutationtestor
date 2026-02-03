#lang racket

(require rackunit
         rackunit/text-ui
         "../src/add1.rkt")

(define tests

  (test-suite
   "Tests for racket-project"

   (check-equal? (add1) "Hello, World!")
   (check-equal? (add1 "<username>") "Hello, <username>!")))

(module+ test
  (void
   (run-tests tests)))
