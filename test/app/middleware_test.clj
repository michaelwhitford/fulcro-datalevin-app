(ns app.middleware-test
  "Tests to verify middleware is properly configured."
  (:require
   [clojure.test :refer [deftest testing is]]
   [app.server.middleware :as mw]))

(deftest save-middleware-is-a-function
  (testing "save-middleware should be a handler function, not a middleware wrapper"
    (is (fn? mw/save-middleware)
        "save-middleware should be a function")))

(deftest delete-middleware-is-a-function
  (testing "delete-middleware should be a handler function, not a middleware wrapper"
    (is (fn? mw/delete-middleware)
        "delete-middleware should be a function")))

(deftest save-middleware-returns-map
  (testing "save-middleware should return a map when called with env"
    (let [test-env {}  ; Minimal env - won't actually save anything
          result   (mw/save-middleware test-env)]
      (is (map? result)
          (str "Expected map but got: " (type result))))))

(deftest delete-middleware-returns-map
  (testing "delete-middleware should return a map when called with env"
    (let [test-env {}  ; Minimal env - won't actually delete anything
          result   (mw/delete-middleware test-env)]
      (is (map? result)
          (str "Expected map but got: " (type result))))))

(comment
  ;; Run in REPL to verify:
  (require '[app.server.middleware :as mw])
  
  ;; These should both be true:
  (fn? mw/save-middleware)
  (fn? mw/delete-middleware)
  
  ;; These should both return maps:
  (type (mw/save-middleware {}))
  (type (mw/delete-middleware {}))
  
  ;; NOT functions!
  (map? (mw/save-middleware {}))
  (map? (mw/delete-middleware {}))
  )
