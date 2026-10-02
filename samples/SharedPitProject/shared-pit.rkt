#lang racket
(define (condition flag) (if flag 1 0))
(define (negate flag) (not flag))
(define (empty-value) (list 1 2))
(define (returned-call) (compute 1))
(define (void-call) (begin (displayln "notice") 1))
(define (constructor-call) (make-widget 1))
