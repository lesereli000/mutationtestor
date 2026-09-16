#lang racket

(provide countdown-to-zero at-or-below?)

; Racket equivalent of: while (n > 0) { count++; n--; }

(define (countdown-to-zero n)
  (if (<= n 0) 0 (+ 1 (countdown-to-zero (- n 1)))))

; Real boundary logic: (<= n limit) is NOT a loop guard, so the
; <= -> < mutation IS generated and WILL appear in the mutation report.

(define (at-or-below? n limit)
  (if (<= n limit) #t #f))


