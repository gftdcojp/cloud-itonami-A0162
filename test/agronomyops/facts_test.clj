(ns agronomyops.facts-test
  (:require [clojure.test :refer [deftest is]]
            [agronomyops.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest all-four-seeded-jurisdictions-have-a-buffer-spec-basis
  ;; matching quarryops/0810's own full blast-safety sub-citation
  ;; coverage, ALL FOUR seeded jurisdictions actually have a real
  ;; water-buffer-zone enforcement regime here -- reported honestly,
  ;; not forced narrower
  (doseq [iso3 ["JPN" "USA" "GBR" "DEU"]]
    (is (some? (facts/buffer-spec-basis iso3)) (str iso3 " buffer-spec-basis"))
    (is (string? (:buffer-provenance (facts/buffer-spec-basis iso3))) (str iso3 " buffer-provenance"))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest unknown-jurisdiction-has-no-buffer-spec-basis
  (is (nil? (facts/buffer-spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))
