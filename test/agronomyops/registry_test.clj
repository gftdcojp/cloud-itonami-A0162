(ns agronomyops.registry-test
  (:require [clojure.test :refer [deftest is]]
            [agronomyops.registry :as r]))

;; ----------------------------- dose-matches-claim? -----------------------------

(deftest matches-when-claim-equals-recompute
  (is (r/dose-matches-claim?
       {:area 10 :label-rate 2.0 :claimed-dose 20.0})))

(deftest mismatches-when-claim-differs-from-recompute
  (is (not (r/dose-matches-claim?
            {:area 20 :label-rate 5.0 :claimed-dose 150.0}))))

(deftest compute-dose-is-a-flat-area-times-rate
  (is (= 20.0 (r/compute-dose {:area 10 :label-rate 2.0}))))

;; ----------------------------- register-sample-collection -----------------------------

(deftest sample-is-a-draft-not-a-real-sample
  (let [result (r/register-sample-collection "visit-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest sample-assigns-sample-number
  (let [result (r/register-sample-collection "visit-1" "JPN" 7)]
    (is (= (get result "sample_number") "JPN-SPL-000007"))
    (is (= (get-in result ["record" "visit_id"]) "visit-1"))
    (is (= (get-in result ["record" "kind"]) "sample-collection-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest sample-validation-rules
  (is (thrown? Exception (r/register-sample-collection "" "JPN" 0)))
  (is (thrown? Exception (r/register-sample-collection "visit-1" "" 0)))
  (is (thrown? Exception (r/register-sample-collection "visit-1" "JPN" -1))))

;; ----------------------------- register-treatment-application -----------------------------

(deftest treatment-is-a-draft-not-a-real-treatment
  (let [result (r/register-treatment-application "visit-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest treatment-assigns-treatment-number
  (let [result (r/register-treatment-application "visit-1" "JPN" 7)]
    (is (= (get result "treatment_number") "JPN-TRT-000007"))
    (is (= (get-in result ["record" "visit_id"]) "visit-1"))
    (is (= (get-in result ["record" "kind"]) "treatment-application-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest treatment-validation-rules
  (is (thrown? Exception (r/register-treatment-application "" "JPN" 0)))
  (is (thrown? Exception (r/register-treatment-application "visit-1" "" 0)))
  (is (thrown? Exception (r/register-treatment-application "visit-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-sample-collection "visit-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-sample-collection "visit-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-SPL-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-SPL-000001" (get-in hist2 [1 "record_id"])))))
