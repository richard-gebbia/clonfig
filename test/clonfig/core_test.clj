(ns clonfig.core-test
  (:require [clojure.test :refer :all]
            [clonfig.core :as cfg]))

;; Don't call any of these tests or this test namespace directly, but use the Makefile instead.

(deftest env-var-basic-test
  ;; preconditions
  (is (= "hello" (System/getenv "FOO")))

  (cfg/defconfig *foo*)
  (cfg/init! [])
  (is (= *foo* "hello"))
  
  ;; teardown
  (ns-unmap *ns* '*foo*))

(deftest cmd-line-basic-test
  (cfg/defconfig *bar*)
  (cfg/init! ["--bar" "hello"])
  (is (= *bar* "hello"))

  ;; teardown
  (ns-unmap *ns* '*bar*))

(deftest cmd-line-priority-test
  ;; preconditions
  (is (= "hello" (System/getenv "FOO")))
  
  (cfg/defconfig *foo*)
  (cfg/init! ["--foo" "world"])
  (is (= *foo* "world"))
  
  ;; teardown
  (ns-unmap *ns* '*foo*))

(deftest cmd-line-short-test
  (cfg/defconfig *bar* :short "-b")
  (cfg/init! ["-b" "world"])
  (is (= *bar* "world"))
  
  ;; teardown
  (ns-unmap *ns* '*bar*))