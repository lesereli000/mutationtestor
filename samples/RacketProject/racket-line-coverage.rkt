#lang racket

(define args (current-command-line-arguments))

(unless (= (vector-length args) 3)
  (error 'racket-line-coverage
         "usage: racket racket-line-coverage.rkt <source-dir> <test-file> <output-lcov>"))

(define source-dir (vector-ref args 0))
(define test-file (vector-ref args 1))
(define output-file (vector-ref args 2))
(define trace-file ".mutation-racket-coverage.tmp")
(define backup-suffix ".mutation-coverage-backup")

(define (source-files dir)
  (for/list ([path (in-directory dir)]
             #:when (and (file-exists? path)
                         (regexp-match? #rx"[.]rkt$" (path->string path))))
    path))

(define (top-level-define-line? line)
  (regexp-match? #rx"^\\(define \\([^)]+" (string-trim line)))

(define (coverage-ranges lines)
  (define starts
    (for/list ([line (in-list lines)]
               [idx (in-naturals 1)]
               #:when (top-level-define-line? line))
      idx))
  (for/list ([start (in-list starts)]
             [next-start (in-list (append (rest starts) (list (+ (length lines) 1))))])
    (cons start (sub1 next-start))))

(define (instrument-file path)
  (define original (file->string path))
  (define lines (string-split original "\n" #:trim? #f))
  (define ranges-by-start (make-hash))
  (for ([range (in-list (coverage-ranges lines))])
    (hash-set! ranges-by-start (car range) (cdr range)))

  (copy-file path (string-append (path->string path) backup-suffix) #t)
  (call-with-output-file path
    #:exists 'truncate
    (lambda (out)
      (for ([line (in-list lines)]
            [idx (in-naturals 1)])
        (fprintf out "~a\n" line)
        (when (= idx 1)
          (fprintf out "(define (mutation-coverage-record file start end)\n")
          (fprintf out "  (call-with-output-file ~s #:exists 'append\n" trace-file)
          (fprintf out "    (lambda (out)\n")
          (fprintf out "      (for ([line (in-range start (+ end 1))])\n")
          (fprintf out "        (fprintf out \"~~a:~~a\\n\" file line)))))\n"))
        (when (hash-has-key? ranges-by-start idx)
          (fprintf out "  (mutation-coverage-record ~s ~a ~a)\n"
                   (path->string path)
                   idx
                   (hash-ref ranges-by-start idx)))))))

(define (restore-file path)
  (define backup (string-append (path->string path) backup-suffix))
  (when (file-exists? backup)
    (copy-file backup path #t)
    (delete-file backup)))

(define files (source-files source-dir))

(when (file-exists? trace-file)
  (delete-file trace-file))

(for ([file (in-list files)])
  (instrument-file file))

(define racket (find-executable-path "racket"))
(define test-ok?
  (with-handlers ([exn:fail? (lambda (_) #f)])
    (system* racket test-file)))

(for ([file (in-list files)])
  (restore-file file))

(unless test-ok?
  (exit 1))

(define covered-lines (make-hash))
(when (file-exists? trace-file)
  (for ([line (in-list (file->lines trace-file))]
        #:when (regexp-match #rx"^(.+):([0-9]+)$" line))
    (match-define (list _ file line-number) (regexp-match #rx"^(.+):([0-9]+)$" line))
    (hash-update! covered-lines file
                  (lambda (existing) (set-add existing (string->number line-number)))
                  (set))))

(call-with-output-file output-file
  #:exists 'truncate
  (lambda (out)
    (for ([(file lines) (in-hash covered-lines)])
      (fprintf out "SF:~a\n" file)
      (for ([line (in-list (sort (set->list lines) <))])
        (fprintf out "DA:~a,1\n" line))
      (fprintf out "end_of_record\n"))))
