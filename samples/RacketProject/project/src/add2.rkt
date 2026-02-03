#lang racket/base

(provide add2)

(require racket/format)

(define (add2 [name "Worl"])
  (~a "Hello, " name "!"))