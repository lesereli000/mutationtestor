#lang racket

(require rackunit
         rackunit/text-ui
         "../src/add1.rkt"
         "../src/add2.rkt")

(define tests

  (test-suite
   "Tests for racket-project"

   (check-equal? (add1 3) 4)
   (check-equal? (add2 3) 5)))

(module+ main
  (void
   (run-tests tests)))