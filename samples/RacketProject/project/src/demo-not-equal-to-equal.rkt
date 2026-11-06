#lang racket

(define (not-equal? a b)
  (not (equal? a b)))

(define (is-not-equal a b)
  (not-equal? a b)) ; (equal? a b)

(define (chained-inequality a b c)
  (and (not-equal? a b) (not-equal? b c))) ; (and (equal? a b) (equal? b c))

(define (array-not-equals v i val)
  (not-equal? (vector-ref v i) val)) ;  (equal? (vector-ref v i) val)

(define (not-null-check s)
  (not-equal? s null)) ; (equal? s null)

(define (test-basic-inequality)
  (let ([x 5]
        [y 10])
    (if (not-equal? x y)
        (displayln "test-basic-inequality: fails (should pass)")
        (displayln "test-basic-inequality: passes (should fail)"))))

(define (test-equality-check)
  (let ([x 5]
        [y 5])
    (if (not-equal? x y)
        (displayln "test-equality-check: passes")
        (displayln "test-equality-check: fails"))))

(define (test-multiple-conditions)
  (let ([a 5]
        [b 10]
        [c 15])
    (if (and (not-equal? a b) (not-equal? b c))
        (displayln "test-multiple-conditions: fails (all different)")
        (displayln "test-multiple-conditions: passes"))))

(displayln "string literal has not-equal?: (not-equal? a b)") ; same (inside string)

; line comment with not-equal? should stay: (not-equal? a b)  ; same (comment)

#|
  block comment with not-equal? should stay: (not-equal? a b)  ; same (comment)
|#

(displayln (is-not-equal 5 10))  ; #t -> #f
(displayln (is-not-equal 5 5))  ; #f -> #t
(displayln (chained-inequality 5 10 15))  ; #t -> #f
(displayln (chained-inequality 5 5 10))  ; #f -> #f
(displayln (array-not-equals #(10 20 30) 1 25))  ; #t -> #f
(displayln (not-null-check null))  ; #f -> #t
(displayln (not-null-check "test"))  ; #t -> #f
(displayln (not-equal? 5 10))  ; (equal? 5 10) -> #f
(displayln (not-equal? 5 5))  ; (equal? 5 5) -> #t
(displayln (and (not-equal? 5 10) (not-equal? 10 15)))  ; (and (equal? 5 10) (equal? 10 15)) -> #f

(test-basic-inequality)
(test-equality-check)
(test-multiple-conditions)