#lang racket
;; Every constructor call here uses a struct declared in shapes.rkt.
(require "shapes.rkt")
(provide origin translate unit-square)

(define (origin) (posn 0 0))

(define (translate p dx dy)
  (posn (+ (posn-x p) dx) (+ (posn-y p) dy)))

(define (unit-square)
  (make-rect (origin) 1 1))
