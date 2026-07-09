(ns agronomyops.registry
  "Pure-function sample-collection + treatment-application record
  construction -- an append-only agronomy book-of-record draft.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a sample-collection or treatment-
  application record -- every operator/jurisdiction assigns its own
  reference format. This namespace does NOT invent one; it builds a
  jurisdiction-scoped sequence number and validates the record's
  required fields, the same honest, non-fabricating discipline
  `agronomyops.facts` uses.

  `dose-matches-claim?` is an HONEST reapplication of the SAME ground-
  truth-recompute DISCIPLINE `quarryops.registry`'s own `royalty-
  matches-claim?`, `leathergoods.registry`'s/`specialtyrepair.
  registry`'s own `parts-cost-matches-claim?` and `retailops.
  registry`'s own `sale-total-matches-claim?` establish (verify a
  claimed quantity against the entity's own recorded area x label-rate
  fields), reapplied to a treatment dose line rather than a royalty,
  repair-parts or retail-sale line -- not claimed as new code, though
  no literal code is shared (different domain).

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real agronomy-operations system. It builds the RECORD an
  operator would keep, not the act of collecting a sample or applying
  a treatment itself (that is `agronomyops.operation`'s `:sample/
  collect`/`:treatment/apply`, always human-gated -- see README
  `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the agronomy operator's act, not this actor's. See README
  `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn compute-dose
  "The ground-truth treatment dose for `visit`'s own `:area` and
  `:label-rate` -- a single flat area x rate calculation, not a full
  agronomic dosing engine."
  [{:keys [area label-rate]}]
  (* (double area) (double label-rate)))

(defn dose-matches-claim?
  "Does `visit`'s own `:claimed-dose` equal the independently
  recomputed `compute-dose`? A pure ground-truth check against the
  visit's own permanent fields -- see ns docstring for why this is an
  honest reapplication of the SAME discipline every sibling actor's
  own cost/total-matching check establishes, not a new concept."
  [{:keys [claimed-dose] :as visit}]
  (== (double claimed-dose) (compute-dose visit)))

(defn register-sample-collection
  "Validate + construct the SAMPLE-COLLECTION registration DRAFT --
  the agronomy operator's own legal act of collecting a real soil/crop
  sample in the field. Pure function -- does not touch any real field-
  robotics system; it builds the RECORD an operator would keep.
  `agronomyops.governor` independently re-verifies the visit's own
  evidence ground truth, and blocks a double-sampling of the same
  record, before this is ever allowed to commit."
  [visit-id jurisdiction sequence]
  (when-not (and visit-id (not= visit-id ""))
    (throw (ex-info "sample-collection: visit_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "sample-collection: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "sample-collection: sequence must be >= 0" {})))
  (let [sample-number (str (str/upper-case jurisdiction) "-SPL-" (zero-pad sequence 6))
        record {"record_id" sample-number
                "kind" "sample-collection-draft"
                "visit_id" visit-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "sample_number" sample-number
     "certificate" (unsigned-certificate "SampleCollection" sample-number sample-number)}))

(defn register-treatment-application
  "Validate + construct the TREATMENT-APPLICATION registration DRAFT --
  the agronomy operator's own legal act of applying a real targeted
  treatment in the field (triggering agrochemical-compliance and
  water-buffer obligations). Pure function -- does not touch any real
  field-robotics system; it builds the RECORD an operator would keep.
  `agronomyops.governor` independently re-verifies the visit's own
  dose/product/buffer ground truth, and blocks a double-treatment of
  the same record, before this is ever allowed to commit."
  [visit-id jurisdiction sequence]
  (when-not (and visit-id (not= visit-id ""))
    (throw (ex-info "treatment-application: visit_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "treatment-application: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "treatment-application: sequence must be >= 0" {})))
  (let [treatment-number (str (str/upper-case jurisdiction) "-TRT-" (zero-pad sequence 6))
        record {"record_id" treatment-number
                "kind" "treatment-application-draft"
                "visit_id" visit-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "treatment_number" treatment-number
     "certificate" (unsigned-certificate "TreatmentApplication" treatment-number treatment-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
