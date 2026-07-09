# cloud-itonami-isic-0162

Open Business Blueprint for **ISIC Rev.5 0162**: agronomy support --
soil and crop scouting, advisory, and targeted treatment for
smallholders.

This repository publishes a community-agronomy actor -- visit intake,
per-jurisdiction agrochemical-registration/water-buffer-zone
regulatory assessment, field sample collection and targeted treatment
application -- as an OSS business that any qualified operator can
fork, deploy, run, improve and sell, so a local agronomy operator
never surrenders sampling and treatment data to a closed SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet (89 prior actors) -- here it is
**AgronomyOps-LLM ⊣ Agronomy Governor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:agronomy-governor`, is a
UNIQUE keyword fleet-wide (grep-verified: no other blueprint declares
it) -- a fresh, independent build.

> **Why an actor layer at all?** An LLM is great at drafting a visit
> summary, normalizing records, and checking whether a claimed
> treatment dose actually equals a field's own recorded area times
> label rate -- but it has **no notion of which jurisdiction's
> agrochemical-registration/water-buffer-zone law is official, no
> license to collect a real sample or apply a real treatment, and no
> way to know on its own whether a treatment product is actually
> approved for the target crop or whether a field's own water-source
> buffer clearance has actually been confirmed**. Letting it collect a
> sample or apply a treatment directly invites fabricated regulatory
> citations, a dose mismatch being applied in the field, an
> unregistered product being sprayed on a crop, and a treatment
> proceeding without a confirmed water-source buffer -- exposing a
> water source to contamination and the operator to real regulatory
> liability. This project seals the AgronomyOps-LLM into a single node
> and wraps it with an independent **Agronomy Governor**, a human
> **approval workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers visit intake through agrochemical-registration/
water-buffer-zone regulatory assessment, field sample collection and
treatment application. It does **not**, by itself, hold any operating
license required to advise on or apply agrochemicals in a given
jurisdiction, and it does not claim to. It also does not perform the
actual physical sampling or spraying work itself, or judge agronomic/
crop-health quality -- `agronomyops.registry/dose-matches-claim?` is a
pure ground-truth recompute against the visit's own recorded fields,
not an agronomic judgment. Whoever deploys and operates a live
instance (a qualified agronomy operator/extension officer) supplies
any jurisdiction-specific license, the real field-robotics/lab
integration and the real farm-record-system integrations, and bears
that jurisdiction's liability -- the software supplies the governed,
spec-cited, audited execution scaffold so that operator does not have
to build the compliance layer from scratch.

### Actuation

**Collecting a real sample and applying a real treatment are never
autonomous, at any phase, by construction.** Two independent layers
enforce this (`agronomyops.governor`'s `:actuation/collect-sample`/
`:actuation/apply-treatment` high-stakes gate and `agronomyops.
phase`'s phase table, which never puts either op in any phase's
`:auto` set) -- see `agronomyops.phase`'s docstring and
`test/agronomyops/phase_test.clj`'s `sample-collect-never-auto-at-any-
phase`/`treatment-apply-never-auto-at-any-phase`. The actor may draft,
check and recommend; a human agronomy operator is always the one who
actually collects a sample or applies a treatment. Grounded directly
in this blueprint's own `docs/business-model.md` Trust Controls text
("treatment records cannot be suppressed; advisory must cite
evidence; field operations stay outside LLM control") -- a genuine
DUAL-actuation shape, applied SEQUENTIALLY to the SAME visit record
(sample first, treat later), matching `freightops`/4920's and
`quarryops`/0810's own sequential shape rather than `retailops`/4711's
own alternative-kind shape.

## The core contract

```
visit intake + jurisdiction facts (agronomyops.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌───────────────────────┐
   │ AgronomyOps-LLM       │ ─────────────▶ │ Agronomy Governor             │  (independent system)
   │ (sealed)              │  + citations    │ spec-basis · evidence-       │
   └───────────────────────┘                 │ incomplete · treatment-      │
          │                 commit ◀┼ dose-mismatch (ground-truth) ·    │
          │                         │ treatment-product-unapproved            │
    record + ledger        escalate ┼ (FLAGSHIP NEW) · water-source-             │
          │              (ALWAYS for│ buffer-violation (conditional,            │
          │       :actuation/collect│ NEW) · already-sampled ·                  │
          │       -sample/          │ already-treated                           │
          │       :actuation/apply- │                                            │
          │       treatment}         │                                            │
          ▼                          └───────────────────────┘
      human approval
```

**The AgronomyOps-LLM never collects a sample or applies a treatment
the Agronomy Governor would reject, and never does so without a human
sign-off.** Hard violations (fabricated regulatory requirements;
unsupported evidence; a dose mismatch; an unapproved treatment
product; an unconfirmed water-source buffer on a near-water-source
visit; a double sampling/treatment) force **hold** and *cannot* be
approved past; a clean sample/treatment proposal still always routes
to a human.

## Run

```bash
clojure -M:dev:run     # walk two clean sample+treatment lifecycles (no water source, near-water-source buffer-compliant), plus four HARD-hold cases, through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a field robot performs
sampling, scouting and targeted treatment in the field, under the
actor, gated by the independent **Agronomy Governor**. The governor
never dispatches hardware itself; `:high`/`:safety-critical` actions
(such as operating near people, livestock or water sources) require
human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Agronomy Governor, sample/treatment draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`0162`). This vertical's service records are practice-specific rather
than a shared cross-operator data contract, so `agronomyops.*` runs on
the generic robotics/identity/forms/dmn/bpmn/audit-ledger/telemetry
stack only -- no bespoke domain capability lib to reference at all
(unlike `retailops`/4711's own `kotoba-lang/retail` and `freightops`/
4920's own `kotoba-lang/logistics` integrations; verified via a GitHub
search API sweep for `kotoba-lang/agronomy`, `agriculture`, `farm`,
`crop` and `soil`-style repos, matching `quarryops`/0810's own
investigated-and-ruled-out precedent).

## Layout

| File | Role |
|---|---|
| `src/agronomyops/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + sample AND treatment history (dual history). The double-actuation guard checks dedicated `:sampled?`/`:treated?` booleans rather than a `:status` value |
| `src/agronomyops/registry.cljc` | Sample/treatment draft records, plus `dose-matches-claim?` -- an honest reapplication of the SAME ground-truth-recompute discipline every sibling actor's own cost/total-matching check establishes |
| `src/agronomyops/facts.cljc` | Per-jurisdiction agrochemical-registration AND water-buffer-zone catalog with an official spec-basis citation per entry, honest coverage reporting -- ALL FOUR seeded jurisdictions have a buffer-zone sub-citation here |
| `src/agronomyops/agronomyopsllm.cljc` | **AgronomyOps-LLM** -- `mock-advisor` ‖ `llm-advisor`; intake/jurisdiction-assessment/sample/treatment proposals |
| `src/agronomyops/governor.cljc` | **Agronomy Governor** -- 5 HARD checks (spec-basis · evidence-incomplete · treatment-dose-mismatch · treatment-product-unapproved, FLAGSHIP NEW, the 78th unconditional-evaluation-discipline grounding · water-source-buffer-violation, CONDITIONAL, the 79th grounding) + 2 double-actuation guards + 1 soft (confidence/actuation gate) |
| `src/agronomyops/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (sample/treatment always human; visit intake is the ONLY auto-eligible op, no direct field risk) |
| `src/agronomyops/operation.cljc` | **OperationActor** -- langgraph StateGraph |
| `src/agronomyops/sim.cljc` | demo driver |
| `test/agronomyops/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers visit intake through agrochemical-registration/
water-buffer-zone regulatory assessment, field sample collection and
treatment application -- the core governed lifecycle this blueprint's
own `docs/business-model.md` names in its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Visit intake + per-jurisdiction evidence checklisting, HARD-gated on an official spec-basis citation (`:visit/intake`/`:jurisdiction/assess`) | Real field-robotics/lab integration, real agronomic/crop-health judgment (see `agronomyops.facts`'s docstring) |
| Sample collection, HARD-gated on full evidence, plus a double-sampling guard (`:actuation/collect-sample`) | |
| Treatment application, HARD-gated on full evidence, a matching dose claim, an approved treatment product and a confirmed water-source buffer (when applicable), plus a double-treatment guard (`:actuation/apply-treatment`) | |
| Immutable audit ledger for every intake/assessment/sample/treatment decision | |

Extending coverage is additive: add the next gate (e.g. a
lab-result-verification check) as its own governed op with its own
HARD checks and tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world act"
pattern this repo's flagship ops already establish.

## Jurisdiction coverage (honest)

`agronomyops.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `agronomyops.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `agronomyops.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger. Note that the water-buffer-zone sub-
citation is FULL coverage rather than a gap: ALL FOUR seeded
jurisdictions (JPN, USA, GBR, DEU) actually have a real water-buffer-
zone enforcement regime, reported honestly.

## Maturity

`:implemented` -- `AgronomyOps-LLM` + `Agronomy Governor` run as real,
tested code (see `Run` above), promoted from the originally-published
`:blueprint`-tier scaffold, following the SAME governed-actor
architecture as the 89 other prior actors across this fleet, with its
own distinct, independently-named governor. See
`docs/adr/0001-architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
