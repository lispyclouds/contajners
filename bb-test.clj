#!/usr/bin/env bb

(require '[clojure.test :as t]
         '[babashka.classpath :as cp])

(cp/add-classpath "src:test")

(require 'contajners.core-test
         'contajners.impl-test)

(def test-results
  (t/run-tests 'contajners.core-test 'contajners.impl-test))

(def failures-and-errors
  (let [{:keys [:fail :error]} test-results]
    (+ fail error)))

(System/exit failures-and-errors)
