#lang racket

(define (is-equal a b)
  (equal? a b)) ;  (not-equal? a b)

(define (chained-equality a b c)
  (and (equal? a b) (equal? b c))) ;  (and (not-equal? a b) (not-equal? b c))

(define (array-equals v i val)
  (equal? (vector-ref v i) val)) ;  (not-equal? (vector-ref v i) val)

(define (null-check s)
  (equal? s null)) ;  (not-equal? s null)

(define (test-basic-equality)
  (let ([x 5]
        [y 5])
    (if (equal? x y)
        (displayln "test-basic-equality: fails (should pass)")
        (displayln "test-basic-equality: passes (should fail)"))))

(define (test-inequality-check)
  (let ([x 5]
        [y 10])
    (if (equal? x y)
        (displayln "test-inequality-check: passes")
        (displayln "test-inequality-check: fails"))))

(define (test-multiple-conditions)
  (let ([a 5]
        [b 5]
        [c 5])
    (if (and (equal? a b) (equal? b c))
        (displayln "test-multiple-conditions: fails (all equal)")
        (displayln "test-multiple-conditions: passes"))))

(displayln "string literal has equal?: (equal? a b)") ; same (inside string)

; line comment with equal? should stay: (equal? a b)  ; same (comment)

#|
  block comment with equal? should stay: (equal? a b)  ; same (comment)
|#

(displayln (is-equal 5 5))  ; #t -> #f
(displayln (is-equal 5 10))  ; #f -> #t
(displayln (chained-equality 5 5 5))  ; #t -> #f
(displayln (chained-equality 5 5 10))  ; #f -> #f
(displayln (array-equals #(10 20 30) 1 20))  ; #t -> #f
(displayln (null-check null))  ; #t -> #f
(displayln (null-check "test"))  ; #f -> #t

(displayln (equal? 5 5))  ; (not-equal? 5 5) -> #f
(displayln (equal? 5 10))  ; (not-equal? 5 10) -> #t
(displayln (and (equal? 5 5) (equal? 10 10)))  ; (and (not-equal? 5 5) (not-equal? 10 10)) -> #f

(test-basic-equality)
(test-inequality-check)
(test-multiple-conditions)