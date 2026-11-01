#lang racket

(define (either-positive a b)
  ;; should mutate here
  (or (> a 0) (> b 0)))

(module+ main
  (displayln (string-append "Either positive? " (if (either-positive 2 -3) "#t" "#f")))
  ;; should not mutate in this string: "foo or bar"
)
