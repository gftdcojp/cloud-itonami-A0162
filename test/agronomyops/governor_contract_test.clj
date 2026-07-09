(ns agronomyops.governor-contract-test
  "The governor contract as executable tests -- this vertical's own
  Trust Controls ('treatment records cannot be suppressed; advisory
  must cite evidence; field operations stay outside LLM control')
  implemented faithfully. The single invariant under test:

    AgronomyOps-LLM never collects a sample or applies a treatment the
    Agronomy Governor would reject, `:sample/collect`/`:treatment/
    apply` NEVER auto-commit at any phase, `:visit/intake` (no direct
    field risk) MAY auto-commit when clean, and every decision (commit
    OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [agronomyops.store :as store]
            [agronomyops.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :agronomy-operator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  "Walks `subject` through assess -> approve, leaving an assessment on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :jurisdiction/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :visit/intake :subject "visit-1"
                   :patch {:id "visit-1" :farm "Kita Farm"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Kita Farm" (:farm (store/visit db "visit-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :jurisdiction/assess :subject "visit-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "visit-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :jurisdiction/assess :subject "visit-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "visit-1")) "no assessment written"))))

(deftest sample-without-assessment-is-held
  (testing "sample/collect before any jurisdiction assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :sample/collect :subject "visit-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest treatment-dose-mismatch-is-held
  (testing "a claimed dose that doesn't equal area x label-rate -> HOLD (the ground-truth-recompute discipline every sibling's cost/total-matching check establishes)"
    (let [[db actor] (fresh)
          _ (assess! actor "t5pre" "visit-3")
          res (exec-op actor "t5" {:op :treatment/apply :subject "visit-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:treatment-dose-mismatch} (-> (store/ledger db) last :basis)))
      (is (empty? (store/treatment-history db))))))

(deftest treatment-product-unapproved-is-held-and-unoverridable
  (testing "an unapproved treatment product -> HOLD, and never reaches request-approval -- the FLAGSHIP genuinely new check this vertical adds, the 78th unconditional-evaluation-discipline grounding overall, grounded in US FIFRA (EPA), UK Plant Protection Products Regulations 2011 (HSE), Germany's Pflanzenschutzgesetz and Japan's own 農薬取締法"
    (let [[db actor] (fresh)
          _ (assess! actor "t6pre" "visit-4")
          res (exec-op actor "t6" {:op :treatment/apply :subject "visit-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:treatment-product-unapproved} (-> (store/ledger db) last :basis)))
      (is (empty? (store/treatment-history db))))))

(deftest water-source-buffer-violation-is-held-and-unoverridable
  (testing "an unconfirmed water-source buffer on a near-water-source visit -> HOLD, and never reaches request-approval -- a genuinely new check, the 79th unconditional-evaluation-discipline grounding overall, the NINTH conditional variant (see this actor's governor ns docstring / the full accumulated ADR-0001 chain: parksafety's ADR-2607071922 Decision 5 through leathergoods's, ictrepair's, retailops's, freightops's and quarryops's own)"
    (let [[db actor] (fresh)
          _ (assess! actor "t7pre" "visit-5")
          res (exec-op actor "t7" {:op :treatment/apply :subject "visit-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:water-source-buffer-violation} (-> (store/ledger db) last :basis)))
      (is (empty? (store/treatment-history db))))))

(deftest treatment-apply-is-a-noop-when-not-near-water
  (testing "the water-source-buffer check is CONDITIONAL: a visit that is not near a water source has no buffer requirement at all"
    (let [[_db actor] (fresh)
          _ (assess! actor "t7bpre" "visit-1")
          res (exec-op actor "t7b" {:op :treatment/apply :subject "visit-1"} operator)]
      (is (= :interrupted (:status res)) "clean treatment still escalates for human sign-off, but is NOT a HARD hold"))))

(deftest treatment-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, matching-dose, approved-product treatment still ALWAYS interrupts for human approval -- actuation/apply-treatment is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t8pre" "visit-1")
          r1 (exec-op actor "t8" {:op :treatment/apply :subject "visit-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, treatment record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:treated? (store/visit db "visit-1"))))
          (is (= 1 (count (store/treatment-history db))) "one draft treatment record"))))))

(deftest sample-always-escalates-then-human-decides
  (testing "a clean, fully-assessed sample collection still ALWAYS interrupts for human approval -- actuation/collect-sample is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t9pre" "visit-1")
          r1 (exec-op actor "t9" {:op :sample/collect :subject "visit-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, sample record drafted"
        (let [r2 (approve! actor "t9")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:sampled? (store/visit db "visit-1"))))
          (is (= 1 (count (store/sample-history db))) "one draft sample record"))))))

(deftest visit-double-sampling-is-held
  (testing "sampling the same visit record twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t10pre" "visit-1")
          _ (exec-op actor "t10a" {:op :sample/collect :subject "visit-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :sample/collect :subject "visit-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-sampled} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/sample-history db))) "still only the one earlier sample"))))

(deftest visit-double-treatment-is-held
  (testing "treating the same visit twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t11pre" "visit-1")
          _ (exec-op actor "t11a" {:op :treatment/apply :subject "visit-1"} operator)
          _ (approve! actor "t11a")
          res (exec-op actor "t11" {:op :treatment/apply :subject "visit-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-treated} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/treatment-history db))) "still only the one earlier treatment"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :visit/intake :subject "visit-1"
                          :patch {:id "visit-1" :farm "Kita Farm"}} operator)
      (exec-op actor "b" {:op :jurisdiction/assess :subject "visit-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
