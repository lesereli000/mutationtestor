#lang racket

(provide (all-defined-out))

;; Variables and Assignments
(define counter 0)

;; Mathematical Operators
(define (math-ops a b)
  (let ([add (+ a b)]
        [sub (- a b)]
        [mul (* a b)]
        [div (quotient a b)]
        [mod (modulo a b)])
    (list add sub mul div mod)))

;; Relational Operators
(define (relational-ops a b)
  (list (= a b)
        (> a b)
        (< a b)
        (>= a b)
        (<= a b)
        (equal? a b)
        (eq? a b)))

;; Logical Operators
(define (logical-ops a b)
  (list (and (> a 0) (> b 0))
        (or (> a 0) (> b 0))
        (not (> a 0))))

;; Bitwise Operators
(define (bitwise-ops a b)
  (list (bitwise-and a b)
        (bitwise-ior a b)
        (bitwise-xor a b)
        (bitwise-not a)
        (arithmetic-shift a 1)
        (arithmetic-shift a -1)))

;; Conditionals
(define (conditional-check a)
  (cond
    [(> a 0) "positive"]
    [(< a 0) "negative"]
    [else "zero"]))

(define (ternary-check a b)
  (if (> a b) a b))

;; Racket Specifics: Tail Recursion
(define (sum-list lst [acc 0])
  (if (null? lst)
      acc
      (sum-list (cdr lst) (+ acc (car lst)))))

;; Racket Specifics: First-class functions & list processing
(define (process-list lst)
  (let ([doubled (map (lambda (x) (* x 2)) lst)]
        [summed (foldl + 0 lst)])
    (list doubled summed)))

;; Racket Specifics: Macro
(define-syntax-rule (when-positive n body ...)
  (when (> n 0)
    body ...))

;; Dynamic typing / duck typing simulation
(define (quack obj)
  (if (hash-has-key? obj 'quack)
      ((hash-ref obj 'quack))
      "Not a duck"))

;; Main runner
(define (main)
  (set! counter (add1 counter))
  (math-ops 10 5)
  (relational-ops 10 5)
  (logical-ops 10 5)
  (bitwise-ops 10 5)
  (conditional-check 10)
  (ternary-check 10 5)
  (sum-list '(1 2 3))
  (process-list '(1 2 3))
  (when-positive 5 (set! counter (add1 counter)))
  
  (define duck (make-hash))
  (hash-set! duck 'quack (lambda () "Quack"))
  (quack duck))
