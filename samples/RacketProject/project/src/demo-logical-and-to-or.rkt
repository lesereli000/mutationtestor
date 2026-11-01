#lang racket

(define (both-positive a b)
  ;; should mutate here
  (and (> a 0) (> b 0)))

(module+ main
  (displayln (string-append "Both positive? " (if (both-positive 2 3) "#t" "#f")))
  ;; should not mutate in this string: "foo and bar"
)
