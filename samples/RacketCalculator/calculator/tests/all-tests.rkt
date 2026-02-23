#lang racket

(require rackunit
         rackunit/text-ui
         "../src/arithmetic.rkt"
         "../src/statistics.rkt")

; Import test suites
(require "arithmetic-test.rkt")
(require "statistics-test.rkt")

(define all-tests
  (test-suite "All Calculator Tests"
    arithmetic-test-suite
    statistics-test-suite))

; Run all tests when this file is executed directly
(module+ main
  (void (run-tests all-tests)))
