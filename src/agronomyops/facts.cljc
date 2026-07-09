(ns agronomyops.facts
  "Per-jurisdiction agrochemical-registration AND water-buffer-zone
  regulatory catalog -- the G2-style spec-basis table the Agronomy
  Governor checks every `:jurisdiction/assess` proposal against ('did
  the advisor cite an OFFICIAL public source for this jurisdiction's
  requirements, or did it invent one?').

  This blueprint's own text (docs/business-model.md's Trust Controls:
  'treatment records cannot be suppressed; advisory must cite
  evidence') and its own README ('operating near people, livestock or
  water sources' requiring human sign-off) name two real, distinct
  regulatory concerns: whether a treatment product is actually
  registered/approved for use on the target crop at all (general
  agrochemical-registration law), and a SEPARATE statutory buffer-zone
  regime specifically protecting water sources from drift/runoff
  during application (independent of the general registration
  framework -- registration law covers which products may be sold and
  used at all; buffer-zone law covers HOW CLOSE to a water source an
  approved product may still be applied). Each jurisdiction entry
  below therefore cites BOTH the general agrochemical-registration law
  AND a SEPARATE water-buffer-zone law.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries. As with
  `quarryops`/0810's own blast-safety sub-citation, `leathergoods`/
  9523's own brand-authenticity sub-citation, `ictrepair`/9511's own
  media-sanitization sub-citation, `retailops`/4711's own unit-pricing
  sub-citation and `freightops`/4920's own cargo-liability-disclosure
  sub-citation, ALL FOUR seeded jurisdictions actually have a real
  water-buffer-zone regime here, reported honestly rather than forcing
  an artificial gap.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  sampling-record/advisory-record/treatment-record evidence set (PLUS
  a buffer-compliance record for every seeded jurisdiction);
  `:legal-basis` / `:owner-authority` / `:provenance` are the G2
  citation the governor requires before any `:jurisdiction/assess`
  proposal can commit. `:buffer-owner-authority` / `:buffer-legal-
  basis` / `:buffer-provenance` are the SEPARATE water-buffer-zone
  citation the governor's `water-source-buffer-violation?` check is
  grounded in."
  {"JPN" {:name "Japan"
          :owner-authority "農林水産省 (Ministry of Agriculture, Forestry and Fisheries, MAFF)"
          :legal-basis "農薬取締法 (Agricultural Chemicals Regulation Act)"
          :national-spec "農薬の登録基準及び使用基準"
          :provenance "https://www.maff.go.jp/j/nouyaku/"
          :required-evidence ["圃場サンプリング記録 (field sampling record)"
                              "助言記録 (advisory record)"
                              "防除処理記録 (treatment application record)"
                              "水源緩衝帯遵守記録 (water-source buffer-compliance record)"]
          :buffer-owner-authority "農林水産省 (MAFF) / 環境省 (Ministry of the Environment)"
          :buffer-legal-basis "水質汚濁防止法 (Water Pollution Prevention Act) 及び農薬登録における水産動植物への影響評価"
          :buffer-provenance "https://www.env.go.jp/water/dojo-noyaku/"}
   "USA" {:name "United States"
          :owner-authority "Environmental Protection Agency (EPA)"
          :legal-basis "Federal Insecticide, Fungicide, and Rodenticide Act (FIFRA, 7 U.S.C. §136 et seq.)"
          :national-spec "EPA pesticide product registration and label requirements"
          :provenance "https://www.epa.gov/pesticide-registration"
          :required-evidence ["Field sampling record"
                              "Advisory record"
                              "Treatment application record"
                              "Water-source buffer-compliance record"]
          :buffer-owner-authority "Environmental Protection Agency (EPA)"
          :buffer-legal-basis "FIFRA label buffer-zone requirements; Clean Water Act NPDES Pesticide General Permit"
          :buffer-provenance "https://www.epa.gov/npdes/pesticide-permitting"}
   "GBR" {:name "United Kingdom"
          :owner-authority "Health and Safety Executive, Chemicals Regulation Division (HSE CRD)"
          :legal-basis "Plant Protection Products Regulations 2011 (retained Regulation (EC) No 1107/2009)"
          :national-spec "HSE CRD product authorization and label conditions"
          :provenance "https://www.hse.gov.uk/pesticides/"
          :required-evidence ["Field sampling record"
                              "Advisory record"
                              "Treatment application record"
                              "Water-source buffer-compliance record"]
          :buffer-owner-authority "Environment Agency"
          :buffer-legal-basis "Buffer-zone conditions in product authorization; Environmental Permitting (England and Wales) Regulations 2016"
          :buffer-provenance "https://www.gov.uk/guidance/pesticides-how-to-prevent-water-pollution"}
   "DEU" {:name "Germany"
          :owner-authority "Bundesamt für Verbraucherschutz und Lebensmittelsicherheit (BVL)"
          :legal-basis "Pflanzenschutzgesetz (Plant Protection Act)"
          :national-spec "Pflanzenschutzmittelzulassung und Anwendungsbestimmungen"
          :provenance "https://www.bvl.bund.de/"
          :required-evidence ["Feldprobenahmeprotokoll (field sampling record)"
                              "Beratungsprotokoll (advisory record)"
                              "Anwendungsprotokoll (treatment application record)"
                              "Gewässerabstandsnachweis (water-source buffer-compliance record)"]
          :buffer-owner-authority "Pflanzenschutzdienste der Länder (state plant protection services)"
          :buffer-legal-basis "Pflanzenschutz-Anwendungsverordnung (PflSchAnwV, mandatory buffer strips near water bodies)"
          :buffer-provenance "https://www.gesetze-im-internet.de/pflschanwv_2016/"}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to collect a
  sample or apply a treatment on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-0162 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `agronomyops.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))

(defn buffer-spec-basis
  "The jurisdiction's water-buffer-zone requirement map, or nil -- nil
  means this jurisdiction has NO formal statutory water-buffer-zone
  regime this catalog is aware of. In this R0 catalog all four seeded
  jurisdictions actually have one (matching `quarryops`/0810's own
  full blast-safety sub-citation coverage), reported honestly."
  [iso3]
  (when-let [sb (spec-basis iso3)]
    (when (:buffer-owner-authority sb)
      (select-keys sb [:buffer-owner-authority :buffer-legal-basis :buffer-provenance]))))
