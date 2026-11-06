#lang racket
(require rackunit "../src/shapes.rkt" "../src/geometry.rkt")

(check-equal? (origin) (posn 0 0))
(check-equal? (translate (posn 1 2) 3 4) (posn 4 6))
(check-equal? (rect-width (unit-square)) 1)
