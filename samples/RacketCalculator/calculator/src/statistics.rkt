#lang racket/base

(provide sum average maximum minimum)

; Statistical operations on lists
(define (sum lst)
  (if (null? lst)
      0
      (apply + lst)))

(define (average lst)
  (if (null? lst)
      0
      (/ (sum lst) (length lst))))

(define (maximum lst)
  (if (null? lst)
      (error "Cannot find maximum of empty list")
      (apply max lst)))

(define (minimum lst)
  (if (null? lst)
      (error "Cannot find minimum of empty list")
      (apply min lst)))
