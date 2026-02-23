#lang info
(define collection "calculator")
(define deps '("base"))
(define build-deps '("rackunit-lib"))
(define pkg-desc "A simple calculator library with basic arithmetic operations")
(define version "1.0")
(define pkg-authors '(mutationtestor))
(define compile-omit-paths '("tests"))
(define test-include-paths '("tests"))
