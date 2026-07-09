(ns agronomyops.governor
  "Agronomy Governor -- the independent compliance layer that earns the
  AgronomyOps-LLM the right to commit. The LLM has no notion of
  jurisdictional agrochemical-registration/water-buffer-zone law,
  whether a visit's own claimed treatment dose actually equals area
  times label-rate, whether a treatment product is actually approved
  for the target crop, whether a visit near a water source actually
  has a confirmed buffer-zone clearance, or when an act stops being a
  draft and becomes a real-world sample collection or treatment
  application, so this MUST be a separate system able to *reject* a
  proposal and fall back to HOLD.

  `:itonami.blueprint/governor` is `:agronomy-governor`, grep-verified
  UNIQUE fleet-wide -- no naming-collision precedent question, a fresh
  independent build following the SAME governed-actor architecture
  (langgraph StateGraph + independent Governor + Phase 0->3 rollout)
  established by `cloud-itonami-isic-6511`.

  This blueprint's own text (docs/business-model.md's own Trust
  Controls: 'treatment records cannot be suppressed; advisory must
  cite evidence; field operations stay outside LLM control') and its
  own README ('operating near people, livestock or water sources'
  requiring human sign-off) name exactly the checks below.

  Seven checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them. The confidence/actuation gate is
  SOFT: it asks a human to look (low confidence / actuation), and the
  human may approve -- but see `agronomyops.phase`: for `:stake
  :actuation/collect-sample`/`:actuation/apply-treatment` (a real
  sample collection or treatment application) NO phase ever allows
  auto-commit either. Two independent layers agree that actuation is
  always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source
                                       (`agronomyops.facts`), or invent
                                       one?
    2. Evidence incomplete         -- for `:sample/collect`/
                                       `:treatment/apply`, has the
                                       jurisdiction actually been
                                       assessed with a full evidence
                                       checklist on file?
    3. Treatment dose mismatch     -- for `:treatment/apply`,
                                       INDEPENDENTLY recompute whether
                                       the visit's own `:claimed-dose`
                                       equals `area x label-rate`
                                       (`agronomyops.registry/dose-
                                       matches-claim?`) -- an HONEST
                                       reapplication of the SAME
                                       ground-truth-recompute
                                       DISCIPLINE `quarryops.
                                       registry`'s/`leathergoods.
                                       registry`'s/`specialtyrepair.
                                       registry`'s/`retailops.
                                       registry`'s own checks
                                       establish, reapplied to a
                                       treatment-dose line -- not
                                       claimed as new.
    4. Treatment product
       unapproved                    -- for `:treatment/apply`,
                                       INDEPENDENTLY verify the
                                       visit's own `:approved-for-
                                       crop?` is true -- the FLAGSHIP
                                       genuinely new check this
                                       vertical adds (grep-verified
                                       absent fleet-wide -- zero hits
                                       for 'treatment-product-
                                       unapproved'/'pesticide-
                                       unregistered' as a governor
                                       check function name), the 78th
                                       distinct application of the
                                       unconditional-evaluation
                                       discipline overall (most
                                       recently `quarryops.governor/
                                       extraction-permit-invalid-
                                       violations` at 76th). Grounded
                                       in real agrochemical-
                                       registration law: the US FIFRA
                                       (enforced by the EPA), the UK's
                                       Plant Protection Products
                                       Regulations 2011 (enforced by
                                       the HSE's Chemicals Regulation
                                       Division), Germany's
                                       Pflanzenschutzgesetz (enforced
                                       by the BVL), and Japan's own
                                       農薬取締法 (Agricultural Chemicals
                                       Regulation Act, enforced by
                                       MAFF) -- directly grounded in
                                       this blueprint's own text
                                       ('advisory must cite evidence').
                                       Evaluated UNCONDITIONALLY (every
                                       treatment needs an approved
                                       product for its target crop).
    5. Water-source buffer
       violation                      -- for `:treatment/apply`, for a
                                       visit whose own record declares
                                       `:near-water-source? true` (i.e.
                                       this visit's field actually
                                       borders a water source -- not
                                       every field does), INDEPENDENTLY
                                       check whether `:buffer-
                                       compliant?` is true. A GENUINELY
                                       NEW concept (grep-verified
                                       absent fleet-wide -- zero hits
                                       for 'water-source-buffer'/
                                       'buffer-zone' as a governor
                                       check function name), the 79th
                                       distinct application overall,
                                       the NINTH conditional variant
                                       (after `socialresearch`/7220's,
                                       `bizassoc`/9411's, `training`/
                                       8549's, `furniture`/9524's,
                                       `specialtyrepair`/9529's,
                                       `leathergoods`/9523's,
                                       `ictrepair`/9511's and
                                       `quarryops`/0810's own, at 63rd,
                                       64th, 66th, 67th, 68th, 69th,
                                       71st and 77th). CONDITIONAL on
                                       the visit's own `:near-water-
                                       source?` ground truth -- a field
                                       with no water source nearby has
                                       no buffer-zone requirement at
                                       all. Grounded in real water-
                                       buffer-zone law: FIFRA label
                                       buffer-zone requirements / Clean
                                       Water Act NPDES Pesticide
                                       General Permit (US EPA), buffer-
                                       zone conditions in product
                                       authorization / Environmental
                                       Permitting Regulations 2016 (UK
                                       Environment Agency), the
                                       Pflanzenschutz-Anwendungs-
                                       verordnung's mandatory buffer
                                       strips near water bodies
                                       (Germany's state plant
                                       protection services), and
                                       Japan's own 水質汚濁防止法 (Water
                                       Pollution Prevention Act) plus
                                       MAFF/environment-ministry
                                       drift-prevention guidance -- ALL
                                       FOUR seeded jurisdictions
                                       actually have a real regime
                                       here, reported honestly
                                       (matching `quarryops`/0810's own
                                       blast-safety, `leathergoods`/
                                       9523's, `ictrepair`/9511's,
                                       `retailops`/4711's and
                                       `freightops`/4920's own full-
                                       coverage sub-citations).
    6. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:sample/collect`/
                                       `:treatment/apply` (REAL acts)
                                       -> escalate.

  Two more guards, double-sampling/double-treatment prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-sampled-violations`/
  `already-treated-violations` refuse to sample/treat the SAME visit
  twice, off dedicated `:sampled?`/`:treated?` facts (never a `:status`
  value) -- the SAME 'check a dedicated boolean, not status'
  discipline every prior governor's guards establish, informed by
  `cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [agronomyops.facts :as facts]
            [agronomyops.registry :as registry]
            [agronomyops.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Collecting a real sample and applying a real treatment are the two
  real-world actuation events this actor performs -- a two-member set,
  matching every sibling's own dual-actuation shape."
  #{:actuation/collect-sample :actuation/apply-treatment})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:sample/collect`/`:treatment/apply`)
  proposal with no spec-basis citation is a HARD violation -- never
  invent a jurisdiction's agrochemical-registration/water-buffer-zone
  requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :sample/collect :treatment/apply} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:sample/collect`/`:treatment/apply`, the jurisdiction's required
  sampling/advisory/treatment/buffer-compliance evidence must actually
  be satisfied -- do not trust the advisor's self-reported confidence
  alone."
  [{:keys [op subject]} st]
  (when (contains? #{:sample/collect :treatment/apply} op)
    (let [v (store/visit st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction v) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(圃場サンプリング記録/助言記録/防除処理記録/水源緩衝帯遵守記録等)が充足していない状態での提案"}]))))

(defn- treatment-dose-mismatch-violations
  "For `:treatment/apply`, INDEPENDENTLY recompute whether the visit's
  own claimed dose equals area x label-rate via
  `agronomyops.registry/dose-matches-claim?` -- needs no proposal
  inspection or stored-verdict lookup at all, an honest reapplication
  of the same discipline every sibling actor's own cost/total-matching
  check establishes."
  [{:keys [op subject]} st]
  (when (= op :treatment/apply)
    (let [v (store/visit st subject)]
      (when-not (registry/dose-matches-claim? v)
        [{:rule :treatment-dose-mismatch
          :detail (str subject " の申告散布量(" (:claimed-dose v)
                      ")が独立再計算値(" (registry/compute-dose v) ")と一致しない")}]))))

(defn- treatment-product-unapproved-violations
  "For `:treatment/apply`, INDEPENDENTLY verify the visit's own
  `:approved-for-crop?` is true -- the flagship genuinely new check
  this vertical adds. Evaluated UNCONDITIONALLY (every treatment needs
  an approved product for its target crop)."
  [{:keys [op subject]} st]
  (when (= op :treatment/apply)
    (let [v (store/visit st subject)]
      (when-not (true? (:approved-for-crop? v))
        [{:rule :treatment-product-unapproved
          :detail (str subject " の防除資材は対象作物(" (:crop v) ")に未承認")}]))))

(defn- water-source-buffer-violation-violations
  "For `:treatment/apply`, for a visit whose own record declares
  `:near-water-source? true`, INDEPENDENTLY check whether `:buffer-
  compliant?` is true -- a genuinely new concept, CONDITIONAL on the
  visit's own `:near-water-source?` ground truth (a field with no
  water source nearby has no buffer-zone requirement at all)."
  [{:keys [op subject]} st]
  (when (= op :treatment/apply)
    (let [v (store/visit st subject)]
      (when (and (true? (:near-water-source? v))
                 (not (true? (:buffer-compliant? v))))
        [{:rule :water-source-buffer-violation
          :detail (str subject " は水源近接圃場だが緩衝帯遵守が未確認 -- 防除提案は進められない")}]))))

(defn- already-sampled-violations
  "For `:sample/collect`, refuses to sample the SAME visit record
  twice, off a dedicated `:sampled?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :sample/collect)
    (when (store/visit-already-sampled? st subject)
      [{:rule :already-sampled
        :detail (str subject " は既にサンプリング済み")}])))

(defn- already-treated-violations
  "For `:treatment/apply`, refuses to treat the SAME visit twice, off
  a dedicated `:treated?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :treatment/apply)
    (when (store/visit-already-treated? st subject)
      [{:rule :already-treated
        :detail (str subject " は既に防除処理済み")}])))

(defn check
  "Censors an AgronomyOps-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (treatment-dose-mismatch-violations request st)
                           (treatment-product-unapproved-violations request st)
                           (water-source-buffer-violation-violations request st)
                           (already-sampled-violations request st)
                           (already-treated-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
