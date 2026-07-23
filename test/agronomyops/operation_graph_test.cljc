(ns agronomyops.operation-graph-test
  "Integration tests for `agronomyops.operation/build` -- proves the REAL
  compiled `langgraph.graph` StateGraph runs end-to-end via
  `langgraph.graph/run*` through commit / hard-hold / escalate-approve /
  escalate-reject routes. No prior test file in this repo exercised
  `operation/build` at all -- every other test covers
  governor/phase/facts/registry/store in isolation, which proves those
  pure functions work but not that the graph wiring actually threads
  them together."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [agronomyops.facts :as facts]
            [agronomyops.operation :as operation]
            [agronomyops.store :as store]))

(def ^:private op-context {:actor-id "operator-01" :phase 3})

(defn- exec
  ([actor tid request] (exec actor tid request op-context))
  ([actor tid request context]
   (g/run* actor {:request request :context context} {:thread-id tid})))

(deftest commit-path-visit-intake-auto-commits-in-phase-3
  (testing ":visit/intake is the only op in phase-3's :auto set -- a
            clean intake proposal commits straight through the REAL
            compiled graph with no interrupt, and the ledger is
            verified EMPTY before the run so the post-run fact is
            genuinely this run's own effect"
    (let [s (store/seed-db)
          actor (operation/build s)]
      (is (empty? (store/ledger s)))
      (let [result (exec actor "t-commit"
                         {:op :visit/intake :subject "visit-test-1"
                          :patch {:id "visit-test-1" :farm "Test Farm"
                                  :jurisdiction "JPN" :status :intake}})
            state (:state result)]
        (is (= :done (:status result)))
        (is (= :commit (:disposition state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger))))
          (is (= :visit/intake (:op (first ledger)))))
        (is (= "Test Farm" (:farm (store/visit s "visit-test-1"))))))))

(deftest hard-hold-no-spec-basis-blocks-before-escalation
  (testing "a :jurisdiction/assess proposal for an unregistered
            jurisdiction (facts/spec-basis returns nil) is a HARD
            governor violation -- the real graph routes straight to
            :hold, never pausing for human approval even though
            :jurisdiction/assess is not in phase-3's :auto set"
    (let [s (store/seed-db)
          actor (operation/build s)]
      (is (empty? (store/ledger s)))
      (let [result (exec actor "t-hold"
                         {:op :jurisdiction/assess :subject "visit-1" :no-spec? true})
            state (:state result)]
        (is (= :done (:status result)) "no interrupt -- HARD holds never pause for approval")
        (is (= :hold (:disposition state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :governor-hold (:t (first ledger))))
          (is (some #{:no-spec-basis} (map :rule (:violations (first ledger))))))))))

(deftest escalate-then-approve-commits-and-genuinely-consults-advisor
  (testing ":jurisdiction/assess is NEVER in any phase's :auto set, so
            even a Governor-clean proposal for a REAL jurisdiction
            (JPN, with a real spec-basis) GENUINELY interrupts
            (checkpointed) at :request-approval -- the ledger stays
            EMPTY until a human resumes it. Also proves the Advisor's
            real proposal (JPN's own spec-basis :provenance string, not
            a hardcoded literal in agronomyops.operation) threads
            through :advise -> :govern -> :decide -> :request-approval
            -> :commit"
    (let [s (store/seed-db)
          actor (operation/build s)]
      (is (empty? (store/ledger s)))
      (let [held (exec actor "t-escalate" {:op :jurisdiction/assess :subject "visit-1"})]
        (is (= :interrupted (:status held)))
        (is (= [:request-approval] (:frontier held)))
        (is (empty? (store/ledger s)) "not yet committed -- awaiting human sign-off")
        (let [approved (g/run* actor {:approval {:status :approved :by "extension-officer-01"}}
                               {:thread-id "t-escalate" :resume? true})
              approved-state (:state approved)]
          (is (= :done (:status approved)))
          (is (= :commit (:disposition approved-state)))
          (let [ledger (store/ledger s)]
            (is (= 1 (count ledger)))
            (is (= :committed (:t (first ledger))))
            (is (= :jurisdiction/assess (:op (first ledger)))))
          (let [assessment (store/assessment-of s "visit-1")]
            (is (some? assessment))
            (is (= (:provenance (facts/spec-basis "JPN")) (:spec-basis assessment))
                "the committed assessment carries the REAL JPN spec-basis's
                own provenance string -- proof the graph genuinely threads
                the Advisor's proposal through rather than hardcoding one")))))))

(deftest escalate-then-reject-holds
  (testing "a human extension officer rejecting an escalated
            :jurisdiction/assess routes to :hold via the
            :request-approval node's own decision, and durably records
            the rejection -- not a hand-rolled parallel path"
    (let [s (store/seed-db)
          actor (operation/build s)
          _held (exec actor "t-reject" {:op :jurisdiction/assess :subject "visit-3"})
          rejected (g/run* actor {:approval {:status :rejected :by "extension-officer-01"}}
                           {:thread-id "t-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:disposition rejected-state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:t (first ledger))))))))

(deftest clean-treatment-apply-escalates-never-auto-commits-even-in-phase-3
  (testing "`:treatment/apply` is deliberately ABSENT from every phase's
            :auto set -- a clean, dose-matching, approved-for-crop,
            evidence-complete visit (visit-1: claimed-dose 20.0 ==
            label-rate 2.0 x area 10, approved-for-crop? true,
            near-water-source? false so no buffer-zone requirement)
            STILL escalates for human sign-off at phase-3 (full
            autonomy), proving the phase gate genuinely enforces this
            permanent structural rule through the compiled graph"
    (let [s (store/seed-db)
          _ (store/commit-record!
             s {:effect :assessment/set
                :path ["visit-1"]
                :payload {:jurisdiction "JPN"
                          :checklist (:required-evidence (facts/spec-basis "JPN"))
                          :spec-basis (:provenance (facts/spec-basis "JPN"))}})
          actor (operation/build s)
          result (exec actor "t-clean-treatment" {:op :treatment/apply :subject "visit-1"})]
      (is (= :interrupted (:status result))
          "even governor-clean, phase-3, high-confidence :treatment/apply
          never auto-commits -- real-world agrochemical actuation is
          always a human call")
      (is (= [:request-approval] (:frontier result)))
      (is (empty? (store/ledger s))))))

(deftest hard-hold-water-source-buffer-violation-through-compiled-graph
  (testing "the flagship water-source-buffer check is genuinely folded
            into the compiled graph's :govern node -- visit-5's own
            :near-water-source? true / :buffer-compliant? false
            combination HARD-blocks a :treatment/apply proposal end to
            end"
    (let [s (store/seed-db)
          actor (operation/build s)
          result (exec actor "t-buffer" {:op :treatment/apply :subject "visit-5"})
          state (:state result)]
      (is (= :done (:status result)) "no interrupt -- HARD holds never pause for approval")
      (is (= :hold (:disposition state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (some #{:water-source-buffer-violation} (map :rule (:violations (first ledger)))))))))
