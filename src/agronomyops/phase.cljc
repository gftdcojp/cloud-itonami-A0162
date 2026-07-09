(ns agronomyops.phase
  "Phase 0->3 staged rollout for the community-agronomy actor.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- visit intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-assess  -- adds jurisdiction assessment writes,
                                 still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:visit/intake` (no field risk yet)
                                 may auto-commit. `:sample/collect`/
                                 `:treatment/apply` NEVER auto-commit,
                                 at any phase.

  `:sample/collect`/`:treatment/apply` are deliberately ABSENT from
  every phase's `:auto` set, including phase 3 -- a permanent
  structural fact, not a rollout milestone still to come. Collecting a
  real field sample and applying a real targeted treatment are the two
  real-world acts this actor performs; both are always a human
  agronomy operator's call. `agronomyops.governor`'s `:actuation/
  collect-sample`/`:actuation/apply-treatment` high-stakes gate
  enforces the same invariant independently -- two layers, not one,
  agree on this. Like every prior sibling's phase 3 `:auto` set, this
  domain has only ONE member (`:visit/intake`) -- no separate no-field-
  risk 'file' lifecycle distinct from the visit itself.")

(def read-ops  #{})
(def write-ops #{:visit/intake :jurisdiction/assess :sample/collect :treatment/apply})

;; NOTE the invariant: `:sample/collect`/`:treatment/apply` are members
;; of `write-ops` (governor-gated like any write) but are NEVER members
;; of any phase's `:auto` set below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"       :writes #{}                                                            :auto #{}}
   1 {:label "assisted-intake" :writes #{:visit/intake}                                                :auto #{}}
   2 {:label "assisted-assess" :writes #{:visit/intake :jurisdiction/assess}                            :auto #{}}
   3 {:label "supervised-auto" :writes write-ops
      :auto #{:visit/intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:sample/collect`/`:treatment/apply` are never auto-eligible at any
    phase, so they always escalate once the governor clears them (or
    hold if the governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map an Agronomy Governor verdict to a base disposition before the
  phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
