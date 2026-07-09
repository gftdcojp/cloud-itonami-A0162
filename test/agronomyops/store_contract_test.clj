(ns agronomyops.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [agronomyops.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "JPN" (:jurisdiction (store/visit s "visit-1"))))
      (is (= 20.0 (:claimed-dose (store/visit s "visit-1"))))
      (is (true? (:approved-for-crop? (store/visit s "visit-1"))))
      (is (false? (:near-water-source? (store/visit s "visit-1"))))
      (is (= 150.0 (:claimed-dose (store/visit s "visit-3"))))
      (is (false? (:approved-for-crop? (store/visit s "visit-4"))))
      (is (true? (:near-water-source? (store/visit s "visit-5"))))
      (is (false? (:buffer-compliant? (store/visit s "visit-5"))))
      (is (true? (:buffer-compliant? (store/visit s "visit-6"))))
      (is (false? (:sampled? (store/visit s "visit-1"))))
      (is (false? (:treated? (store/visit s "visit-1"))))
      (is (= ["visit-1" "visit-2" "visit-3" "visit-4" "visit-5" "visit-6"]
             (mapv :id (store/all-visits s))))
      (is (nil? (store/assessment-of s "visit-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/sample-history s)))
      (is (= [] (store/treatment-history s)))
      (is (zero? (store/next-sample-sequence s "JPN")))
      (is (zero? (store/next-treatment-sequence s "JPN")))
      (is (false? (store/visit-already-sampled? s "visit-1")))
      (is (false? (store/visit-already-treated? s "visit-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :visit/upsert
                                 :value {:id "visit-1" :farm "Kita Farm"}})
        (is (= "Kita Farm" (:farm (store/visit s "visit-1"))))
        (is (= 20.0 (:claimed-dose (store/visit s "visit-1"))) "unrelated field preserved"))
      (testing "assessment payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["visit-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "visit-1"))))
      (testing "sample drafts a record and advances the sample sequence"
        (store/commit-record! s {:effect :visit/mark-sampled :path ["visit-1"]})
        (is (= "JPN-SPL-000000" (get (first (store/sample-history s)) "record_id")))
        (is (= "sample-collection-draft" (get (first (store/sample-history s)) "kind")))
        (is (true? (:sampled? (store/visit s "visit-1"))))
        (is (= 1 (count (store/sample-history s))))
        (is (= 1 (store/next-sample-sequence s "JPN")))
        (is (true? (store/visit-already-sampled? s "visit-1"))))
      (testing "treatment drafts a record and advances the treatment sequence"
        (store/commit-record! s {:effect :visit/mark-treated :path ["visit-1"]})
        (is (= "JPN-TRT-000000" (get (first (store/treatment-history s)) "record_id")))
        (is (= "treatment-application-draft" (get (first (store/treatment-history s)) "kind")))
        (is (true? (:treated? (store/visit s "visit-1"))))
        (is (= 1 (count (store/treatment-history s))))
        (is (= 1 (store/next-treatment-sequence s "JPN")))
        (is (true? (store/visit-already-treated? s "visit-1"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/visit s "nope")))
    (is (= [] (store/all-visits s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/sample-history s)))
    (is (= [] (store/treatment-history s)))
    (is (zero? (store/next-sample-sequence s "JPN")))
    (is (zero? (store/next-treatment-sequence s "JPN")))
    (store/with-visits s {"x" {:id "x" :farm "f" :crop :rice
                               :area 1 :label-rate 1.0 :claimed-dose 1.0
                               :approved-for-crop? true
                               :near-water-source? false :buffer-compliant? false
                               :sampled? false :treated? false
                               :jurisdiction "JPN" :status :intake}})
    (is (= "f" (:farm (store/visit s "x"))))))
