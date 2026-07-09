(ns agronomyops.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean visit through
  intake -> jurisdiction assessment -> sample collection (escalate/
  approve/commit) -> treatment application (escalate/approve/commit),
  then a SEPARATE clean near-water-source visit through the same
  lifecycle (demonstrating the conditional water-source-buffer check
  passing cleanly), then shows HARD-hold scenarios: a jurisdiction
  with no spec-basis, a treatment-dose mismatch (verified first), an
  unapproved treatment product, and an unconfirmed water-source
  buffer on a near-water-source visit, a double sampling, and a
  double treatment.

  Like `retailops`/4711's, `freightops`/4920's and `quarryops`/0810's
  own new checks, this actor's new checks (`treatment-product-
  unapproved?`, `water-source-buffer-violation?`) are evaluated
  directly at `:treatment/apply` time rather than via a separate
  screening op -- a real treatment decision validates product approval
  and buffer compliance at the point of the act itself. Each check is
  still exercised directly and independently below, one visit per
  HARD-hold scenario, following the SAME 'exercise the failure mode
  directly, never only via a happy-path actuation' discipline
  `parksafety`'s ADR-2607071922 Decision 5 and every sibling since
  establish."
  (:require [langgraph.graph :as g]
            [agronomyops.store :as store]
            [agronomyops.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :agronomy-operator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== visit/intake visit-1 (JPN, clean, no water source) ==")
    (println (exec-op actor "t1" {:op :visit/intake :subject "visit-1"
                                  :patch {:id "visit-1" :farm "Kita Farm"}} operator))

    (println "== jurisdiction/assess visit-1 (escalates -- human approves) ==")
    (println (exec-op actor "t2" {:op :jurisdiction/assess :subject "visit-1"} operator))
    (println (approve! actor "t2"))

    (println "== sample/collect visit-1 (always escalates -- actuation/collect-sample) ==")
    (let [r (exec-op actor "t3" {:op :sample/collect :subject "visit-1"} operator)]
      (println r)
      (println "-- human agronomy operator approves --")
      (println (approve! actor "t3")))

    (println "== treatment/apply visit-1 (always escalates -- actuation/apply-treatment) ==")
    (let [r (exec-op actor "t4" {:op :treatment/apply :subject "visit-1"} operator)]
      (println r)
      (println "-- human agronomy operator approves --")
      (println (approve! actor "t4")))

    (println "== visit/intake visit-6 (JPN, clean, near water source, buffer compliant) ==")
    (println (exec-op actor "t5" {:op :visit/intake :subject "visit-6"
                                  :patch {:id "visit-6" :farm "Chuo Farm"}} operator))

    (println "== jurisdiction/assess visit-6 (escalates -- human approves) ==")
    (println (exec-op actor "t6" {:op :jurisdiction/assess :subject "visit-6"} operator))
    (println (approve! actor "t6"))

    (println "== treatment/apply visit-6 (near water source, buffer compliant -- escalates -- human approves) ==")
    (println (exec-op actor "t7" {:op :treatment/apply :subject "visit-6"} operator))
    (println (approve! actor "t7"))

    (println "== jurisdiction/assess visit-2 (no spec-basis -> HARD hold) ==")
    (println (exec-op actor "t8" {:op :jurisdiction/assess :subject "visit-2" :no-spec? true} operator))

    (println "== jurisdiction/assess visit-3 (escalates -- human approves; sets up the dose-mismatch test) ==")
    (println (exec-op actor "t9" {:op :jurisdiction/assess :subject "visit-3"} operator))
    (println (approve! actor "t9"))

    (println "== treatment/apply visit-3 (claimed 150.0 vs recompute 100.0 -> HARD hold) ==")
    (println (exec-op actor "t10" {:op :treatment/apply :subject "visit-3"} operator))

    (println "== jurisdiction/assess visit-4 (escalates -- human approves; sets up the unapproved-product test) ==")
    (println (exec-op actor "t11" {:op :jurisdiction/assess :subject "visit-4"} operator))
    (println (approve! actor "t11"))

    (println "== treatment/apply visit-4 (product unapproved for crop -> HARD hold) ==")
    (println (exec-op actor "t12" {:op :treatment/apply :subject "visit-4"} operator))

    (println "== jurisdiction/assess visit-5 (escalates -- human approves; sets up the water-source-buffer test) ==")
    (println (exec-op actor "t13" {:op :jurisdiction/assess :subject "visit-5"} operator))
    (println (approve! actor "t13"))

    (println "== treatment/apply visit-5 (near water source, buffer unconfirmed -> HARD hold) ==")
    (println (exec-op actor "t14" {:op :treatment/apply :subject "visit-5"} operator))

    (println "== sample/collect visit-1 AGAIN (double-sampling -> HARD hold) ==")
    (println (exec-op actor "t15" {:op :sample/collect :subject "visit-1"} operator))

    (println "== treatment/apply visit-1 AGAIN (double-treatment -> HARD hold) ==")
    (println (exec-op actor "t16" {:op :treatment/apply :subject "visit-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft sample-collection records ==")
    (doseq [r (store/sample-history db)] (println r))

    (println "== draft treatment-application records ==")
    (doseq [r (store/treatment-history db)] (println r))))
