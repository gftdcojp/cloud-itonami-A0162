# ADR-0001: AgronomyOps-LLM ⊣ Agronomy Governor architecture

## Status

Accepted. `cloud-itonami-isic-0162` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-0162` publishes an OSS business blueprint for
community agronomy support (soil and crop scouting, advisory, and
targeted treatment for smallholders). Like every prior actor in this
fleet, the blueprint alone is not an implementation: this ADR records
the governed-actor architecture that promotes it to real, tested code,
following the same langgraph StateGraph + independent Governor + Phase
0→3 rollout pattern established by `cloud-itonami-isic-6511` (life
insurance) and applied across 89 prior siblings, most recently
`cloud-itonami-isic-0810` (community quarry and stone supply).

Like `quarryops`/0810, this vertical has no bespoke domain capability
library in `kotoba-lang` to wrap (verified: no `kotoba-lang/agronomy`,
`kotoba-lang/agriculture`, `kotoba-lang/farm`, `kotoba-lang/crop` or
`kotoba-lang/soil`-style repo exists via GitHub search API;
`kotoba-lang/robotics` is the generic cross-cutting robotics contract
every cloud-itonami vertical already uses, not a domain-specific
library for this vertical). This build returns to self-contained
domain logic, the same pattern the majority of this fleet's actors
use. This is also the first agriculture-sector (ISIC section A) actor
in the fleet -- every neighboring ISIC 01xx entry remains `:spec`-tier.

This blueprint's own `:itonami.blueprint/governor` keyword,
`:agronomy-governor`, is grep-verified UNIQUE fleet-wide -- no naming-
collision precedent question, a fresh independent build.

## Decision

### Decision 1: fresh governor identity, no reuse precedent needed

`:agronomy-governor` is grep-verified unique across every
blueprint.edn in this fleet. This build follows the SAME governed-
actor architecture as every prior actor, but with its own distinct
governor identity.

### Decision 2: dual-actuation shape, SEQUENTIAL on the SAME `visit` entity

This blueprint's own operating states ("intake : sample : advise :
treat : record : audit") and its own README ("field sampling and
targeted treatment in the field") name two real-world acts: collecting
a sample and applying a treatment. These apply SEQUENTIALLY to the
SAME `visit` entity -- sample first, treat later -- matching
`freightops`/4920's and `quarryops`/0810's own sequential shape
(dispatch-then-settle, extract-then-ship) rather than `retailops`/
4711's own alternative-kind shape (sale-or-reorder). `high-stakes` is
`#{:actuation/collect-sample :actuation/apply-treatment}`.

### Decision 3: `dose-matches-claim?` -- an honest reapplication of the ground-truth-recompute discipline

`agronomyops.registry/dose-matches-claim?` (visit's own claimed
treatment dose vs. area x label-rate) applies the SAME discipline
`quarryops.registry`'s own `royalty-matches-claim?`, `leathergoods.
registry`'s/`specialtyrepair.registry`'s own `parts-cost-matches-
claim?` and `retailops.registry`'s own `sale-total-matches-claim?`
establish -- verify a claimed quantity against the entity's own
recorded fields, independent of proposal inspection. No literal code
is shared (different domain), but the discipline is the same,
documented as such rather than claimed as a novel invention.

### Decision 4: entity and op shape

The primary entity is a `visit`. Four ops: `:visit/intake` (directory
upsert, no field risk), `:jurisdiction/assess` (per-jurisdiction
agrochemical-registration/water-buffer-zone evidence checklist, never
auto), `:sample/collect` (POSITIVE, high-stakes), and `:treatment/
apply` (POSITIVE, high-stakes).

### Decision 5: `treatment-product-unapproved?` -- the 78th unconditional-evaluation grounding, the FLAGSHIP genuinely new check

Grep-verified absent fleet-wide (zero hits for `treatment-product-
unapproved`, `pesticide-unregistered` as a governor check name).
Grounded in real agrochemical-registration law: the US FIFRA (enforced
by the EPA), the UK's Plant Protection Products Regulations 2011
(enforced by the HSE's Chemicals Regulation Division), Germany's
Pflanzenschutzgesetz (enforced by the BVL), and Japan's own 農薬取締法
(Agricultural Chemicals Regulation Act, enforced by MAFF) -- directly
grounded in this blueprint's own text ("advisory must cite evidence").
Evaluated UNCONDITIONALLY on every `:treatment/apply` (every treatment
needs an approved product for its target crop).

### Decision 6: `water-source-buffer-violation?` -- the 79th unconditional-evaluation grounding, the NINTH conditional variant

Before writing this check, every prior sibling's governor namespace
was grepped for any check function named `water-source-buffer`,
`buffer-zone` or `drift` -- zero hits, confirming this is a genuinely
new concept. This is the NINTH conditional variant (after
`socialresearch`/7220's, `bizassoc`/9411's, `training`/8549's,
`furniture`/9524's, `specialtyrepair`/9529's, `leathergoods`/9523's,
`ictrepair`/9511's and `quarryops`/0810's own, at 63rd, 64th, 66th,
67th, 68th, 69th, 71st and 77th) -- CONDITIONAL on the visit's own
`:near-water-source?` ground truth: a field with no water source
nearby has no buffer-zone requirement at all, only fields that
actually border one do. Grounded in real water-buffer-zone law: FIFRA
label buffer-zone requirements / Clean Water Act NPDES Pesticide
General Permit (US EPA), buffer-zone conditions in product
authorization / Environmental Permitting Regulations 2016 (UK
Environment Agency), the Pflanzenschutz-Anwendungsverordnung's
mandatory buffer strips near water bodies (Germany's state plant
protection services), and Japan's own 水質汚濁防止法 (Water Pollution
Prevention Act) plus MAFF/environment-ministry drift-prevention
guidance. Unlike some prior repair-shop-cluster siblings' own honest
single-jurisdiction gap, ALL FOUR seeded jurisdictions actually have a
real regime here, reported honestly (matching `quarryops`/0810's own
blast-safety, `leathergoods`/9523's own, `ictrepair`/9511's own,
`retailops`/4711's own and `freightops`/4920's own full-coverage
sub-citations).

### Decision 7: dedicated double-actuation-guard booleans

`:sampled?`/`:treated?` are dedicated booleans on the `visit` record,
never a single `:status` value -- the same discipline every prior
governor's guards establish, informed by `cloud-itonami-isic-6492`'s
real status-lifecycle bug (ADR-2607071320).

### Decision 8: Store protocol, MemStore + DatomicStore parity

`agronomyops.store/Store` is implemented by both `MemStore` (atom-
backed, default for dev/tests/demo) and `DatomicStore` (`langchain.
db`-backed), proven to satisfy the same contract in
`test/agronomyops/store_contract_test.clj`.

### Decision 9: no bespoke domain capability lib; one genuine `blueprint.edn` field-sync gap found and fixed

Verified explicitly this session via the GitHub search API: no
`kotoba-lang/agronomy`, `kotoba-lang/agriculture`, `kotoba-lang/farm`,
`kotoba-lang/crop` or `kotoba-lang/soil`-style bespoke capability
library exists; `kotoba-lang/robotics` is the GENERIC cross-cutting
robotics contract every cloud-itonami vertical implicitly uses, not
domain-specific to this vertical. This repo's `blueprint.edn` had the
correct `:required-technologies` matching the `kotoba-lang/industry`
registry's own entry for `"0162"` exactly, but was MISSING
`:optional-technologies [:optimization]` entirely (the registry's own
entry has it; `blueprint.edn` had no `:optional-technologies` key at
all) -- found by reading the full registry entry carefully (applying
the lesson from `freightops`/4920's own self-caught field-sync error:
read the complete entry before concluding a field is absent) and fixed
alongside the `:maturity` flip in the same commit.

### Decision 10: mock + LLM advisor pair

`agronomyops.agronomyopsllm` provides `mock-advisor` (deterministic,
default everywhere -- the actor graph and governor contract run
offline) and `llm-advisor` (backed by `langchain.model/ChatModel`,
with a defensive EDN-proposal parser so a malformed LLM response
degrades to a safe low-confidence noop rather than ever auto-
collecting a sample or auto-applying a treatment).

## Alternatives considered

- **An unconditional water-source-buffer check** (applying to every
  treatment regardless of whether the field actually borders a water
  source). Rejected: a field with no water source nearby has no
  buffer-zone concern at all -- forcing the check onto every treatment
  would fabricate a requirement.
- **Fabricating a jurisdiction gap** to match the pattern of some
  prior siblings' own single-jurisdiction honesty gap. Rejected: the
  same honesty discipline that forbids fabricating coverage also
  forbids under-reporting it.
- **Checking for a bespoke capability library that doesn't exist.**
  Considered and explicitly ruled out via a GitHub search API sweep
  (agronomy/agriculture/farm/crop/soil) -- this build correctly
  returns to self-contained domain logic rather than forcing a false
  capability-library integration.

## Consequences

- 90th actor in this fleet (89 implemented before this build). First
  agriculture-sector (ISIC section A) actor in the fleet.
- Establishes two genuinely NEW unconditional-evaluation-discipline
  checks: `treatment-product-unapproved?` (FLAGSHIP, 78th distinct
  application overall) and `water-source-buffer-violation?` (79th
  distinct application overall, the NINTH conditional variant).
- `MemStore` ‖ `DatomicStore` parity is proven by
  `test/agronomyops/store_contract_test.clj`.
- 39 tests / 176 assertions pass; lint is clean; the demo
  (`clojure -M:dev:run`) walks two clean sample+treatment lifecycles
  (no-water-source, near-water-source-buffer-compliant), plus four
  HARD-hold scenarios, end-to-end.
- `blueprint.edn` needed a genuine field-sync fix this time (a missing
  `:optional-technologies [:optimization]` key) in addition to the
  `:maturity` flip.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of
  the general governed-actor architecture pattern)
- `cloud-itonami-isic-0810/docs/adr/0001-architecture.md` (most recent
  prior sibling, template for this ADR's structure)
- Federal Insecticide, Fungicide, and Rodenticide Act (FIFRA), 7 U.S.C. §136 et seq. (US)
- Plant Protection Products Regulations 2011; Environmental Permitting (England and Wales) Regulations 2016 (UK)
- Pflanzenschutzgesetz; Pflanzenschutz-Anwendungsverordnung (PflSchAnwV) (Germany)
- 農薬取締法 (Agricultural Chemicals Regulation Act); 水質汚濁防止法 (Water Pollution Prevention Act) (Japan)
