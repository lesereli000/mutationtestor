#lang typed/racket

(provide typed-add typed-multiply typed-divide check-types mixed-types)

; TYPE_INT_TO_FLOAT: Integer -> Float
(: typed-add (-> Integer Integer Integer))
(define (typed-add x y)
  (+ x y))

; TYPE_FLOAT_TO_INT: Float -> Integer
(: typed-multiply (-> Float Float Float))
(define (typed-multiply x y)
  (* x y))

; TYPE_INT_TO_DOUBLE: Integer types
(: typed-divide (-> Integer Integer Integer))
(define (typed-divide x y)
  (quotient x y))

; Multiple type annotations
(: check-types (-> Integer Float String))
(define (check-types a b)
  (if (> a 0)
      "positive"
      "non-positive"))

; Mixed types in function
(: mixed-types (-> Integer Float Integer Float Float))
(define (mixed-types a b c d)
  (+ (* a b) (* c d)))
