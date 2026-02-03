#lang racket/base

(provide add1)

(require racket/format)

(define (add1 [name "World"])
  (~a "Hello, " name "!"))