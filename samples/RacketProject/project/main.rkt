#lang racket/base

(require "src/add1.rkt"
         "src/add2.rkt")


; run with ' Racket .\samples\RacketProject\project\main.rkt '
(module+ main
  (displayln (add1 (add2 3))))
