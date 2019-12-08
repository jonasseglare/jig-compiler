(ns jig-compiler.binpack-test
  (:require [jig-compiler.binpack :refer :all]
            [clojure.test :refer :all]))

(deftest test-packing
  (let [src (range 15)
        result (pack-bins 20 identity src)]
    (is (= (sort (flatten result))
           src))
    (is (every? (complement empty?) result))
    (is (every? vector? result))
    (is (every? #(<= (apply + %) 20) result)))
  (is (= (pack-bins 20 identity (range 3))
         [[2 1 0]]))
  (is (= (pack-bins 20 :a (for [x (range 3)] {:a x}))
         [[{:a 2} {:a 1} {:a 0}]])))

