(ns agronomyops.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:sample/collect`/`:treatment/apply` must NEVER be a
  member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [agronomyops.phase :as phase]))

(deftest sample-collect-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real sample collection"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :sample/collect))
          (str "phase " n " must not auto-commit :sample/collect")))))

(deftest treatment-apply-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real treatment application"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :treatment/apply))
          (str "phase " n " must not auto-commit :treatment/apply")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-field-risk-ops
  (testing ":visit/intake carries no direct field risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:visit/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :visit/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :sample/collect} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :treatment/apply} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :visit/intake} :commit)))))
