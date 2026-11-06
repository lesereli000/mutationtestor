#lang racket
;; The structs live here, apart from the code that uses them.
(provide (all-defined-out))

(struct posn (x y) #:transparent)
(define-struct rect (corner width height) #:transparent)
