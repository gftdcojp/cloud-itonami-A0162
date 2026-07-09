(ns agronomyops.store
  "SSoT for the community-agronomy actor, behind a `Store` protocol so
  the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/agronomyops/store_contract_test.clj), which is the whole point:
  the actor, the Agronomy Governor and the audit ledger never know
  which SSoT they run on.

  Like `quarryops`/0810's own `extraction`, the primary entity here is
  a `visit` -- sample-collection and treatment-application actuation
  events apply SEQUENTIALLY to the SAME visit record (sample first,
  treat later), matching the freight/quarry cluster's own sequential
  entity shape. Dedicated double-actuation-guard booleans
  (`:sampled?`/`:treated?`, never a `:status` value).

  The ledger stays append-only on every backend: 'which visit was
  screened for an unapproved treatment product or an unconfirmed
  water-source buffer, which sample was collected, which treatment was
  applied, on what jurisdictional basis, approved by whom' is always a
  query over an immutable log -- the audit trail a smallholder or
  cooperative trusting an agronomy operator needs, and the evidence an
  operator needs if a sample or a treatment is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [agronomyops.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (visit [s id])
  (all-visits [s])
  (assessment-of [s visit-id] "committed jurisdiction assessment, or nil")
  (ledger [s])
  (sample-history [s] "the append-only sample-collection history (agronomyops.registry drafts)")
  (treatment-history [s] "the append-only treatment-application history (agronomyops.registry drafts)")
  (next-sample-sequence [s jurisdiction] "next sample-number sequence for a jurisdiction")
  (next-treatment-sequence [s jurisdiction] "next treatment-number sequence for a jurisdiction")
  (visit-already-sampled? [s visit-id] "has this visit already been sampled?")
  (visit-already-treated? [s visit-id] "has this visit's treatment already been applied?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-visits [s visits] "replace/seed the visit directory (map id->visit)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained visit set covering both actuation
  lifecycles (sample, treatment) plus the governor's own new checks,
  so the actor + tests run offline."
  []
  {:visits
   {"visit-1" {:id "visit-1" :farm "Kita Farm" :crop :rice
               :area 10 :label-rate 2.0 :claimed-dose 20.0
               :approved-for-crop? true
               :near-water-source? false :buffer-compliant? false
               :sampled? false :treated? false
               :jurisdiction "JPN" :status :intake}
    "visit-2" {:id "visit-2" :farm "Atlantis Farm" :crop :rice
               :area 5 :label-rate 2.0 :claimed-dose 10.0
               :approved-for-crop? true
               :near-water-source? false :buffer-compliant? false
               :sampled? false :treated? false
               :jurisdiction "ATL" :status :intake}
    "visit-3" {:id "visit-3" :farm "Minami Farm" :crop :soybean
               :area 20 :label-rate 5.0 :claimed-dose 150.0
               :approved-for-crop? true
               :near-water-source? false :buffer-compliant? false
               :sampled? false :treated? false
               :jurisdiction "JPN" :status :intake}
    "visit-4" {:id "visit-4" :farm "Higashi Farm" :crop :wheat
               :area 8 :label-rate 2.0 :claimed-dose 16.0
               :approved-for-crop? false
               :near-water-source? false :buffer-compliant? false
               :sampled? false :treated? false
               :jurisdiction "JPN" :status :intake}
    "visit-5" {:id "visit-5" :farm "Nishi Farm" :crop :rice
               :area 12 :label-rate 2.0 :claimed-dose 24.0
               :approved-for-crop? true
               :near-water-source? true :buffer-compliant? false
               :sampled? false :treated? false
               :jurisdiction "JPN" :status :intake}
    "visit-6" {:id "visit-6" :farm "Chuo Farm" :crop :rice
               :area 15 :label-rate 2.0 :claimed-dose 30.0
               :approved-for-crop? true
               :near-water-source? true :buffer-compliant? true
               :sampled? false :treated? false
               :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- collect-sample!
  "Backend-agnostic `:visit/mark-sampled` -- looks up the visit via the
  protocol and drafts the sample-collection record, and returns
  {:result .. :visit-patch ..} for the caller to persist."
  [s visit-id]
  (let [v (visit s visit-id)
        seq-n (next-sample-sequence s (:jurisdiction v))
        result (registry/register-sample-collection visit-id (:jurisdiction v) seq-n)]
    {:result result
     :visit-patch {:sampled? true
                  :sample-number (get result "sample_number")}}))

(defn- apply-treatment!
  "Backend-agnostic `:visit/mark-treated` -- looks up the visit via the
  protocol and drafts the treatment-application record, and returns
  {:result .. :visit-patch ..} for the caller to persist."
  [s visit-id]
  (let [v (visit s visit-id)
        seq-n (next-treatment-sequence s (:jurisdiction v))
        result (registry/register-treatment-application visit-id (:jurisdiction v) seq-n)]
    {:result result
     :visit-patch {:treated? true
                  :treatment-number (get result "treatment_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (visit [_ id] (get-in @a [:visits id]))
  (all-visits [_] (sort-by :id (vals (:visits @a))))
  (assessment-of [_ visit-id] (get-in @a [:assessments visit-id]))
  (ledger [_] (:ledger @a))
  (sample-history [_] (:sample-records @a))
  (treatment-history [_] (:treatment-records @a))
  (next-sample-sequence [_ jurisdiction] (get-in @a [:sample-sequences jurisdiction] 0))
  (next-treatment-sequence [_ jurisdiction] (get-in @a [:treatment-sequences jurisdiction] 0))
  (visit-already-sampled? [_ visit-id] (boolean (get-in @a [:visits visit-id :sampled?])))
  (visit-already-treated? [_ visit-id] (boolean (get-in @a [:visits visit-id :treated?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :visit/upsert
      (swap! a update-in [:visits (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :visit/mark-sampled
      (let [visit-id (first path)
            {:keys [result visit-patch]} (collect-sample! s visit-id)
            jurisdiction (:jurisdiction (visit s visit-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:sample-sequences jurisdiction] (fnil inc 0))
                       (update-in [:visits visit-id] merge visit-patch)
                       (update :sample-records registry/append result))))
        result)

      :visit/mark-treated
      (let [visit-id (first path)
            {:keys [result visit-patch]} (apply-treatment! s visit-id)
            jurisdiction (:jurisdiction (visit s visit-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:treatment-sequences jurisdiction] (fnil inc 0))
                       (update-in [:visits visit-id] merge visit-patch)
                       (update :treatment-records registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-visits [s visits] (when (seq visits) (swap! a assoc :visits visits)) s))

(defn seed-db
  "A MemStore seeded with the demo visit set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :sample-sequences {} :sample-records []
                           :treatment-sequences {} :treatment-records []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment payloads, ledger facts,
  sample/treatment records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:visit/id                     {:db/unique :db.unique/identity}
   :assessment/visit-id          {:db/unique :db.unique/identity}
   :ledger/seq                   {:db/unique :db.unique/identity}
   :sample-record/seq            {:db/unique :db.unique/identity}
   :treatment-record/seq         {:db/unique :db.unique/identity}
   :sample-sequence/jurisdiction    {:db/unique :db.unique/identity}
   :treatment-sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- visit->tx [{:keys [id farm crop area label-rate claimed-dose
                          approved-for-crop?
                          near-water-source? buffer-compliant?
                          sampled? treated?
                          jurisdiction status sample-number treatment-number]}]
  (cond-> {:visit/id id}
    farm                                          (assoc :visit/farm farm)
    crop                                             (assoc :visit/crop crop)
    area                                                (assoc :visit/area area)
    label-rate                                            (assoc :visit/label-rate label-rate)
    claimed-dose                                             (assoc :visit/claimed-dose claimed-dose)
    (some? approved-for-crop?)                                  (assoc :visit/approved-for-crop? approved-for-crop?)
    (some? near-water-source?)                                     (assoc :visit/near-water-source? near-water-source?)
    (some? buffer-compliant?)                                         (assoc :visit/buffer-compliant? buffer-compliant?)
    (some? sampled?)                                                     (assoc :visit/sampled? sampled?)
    (some? treated?)                                                        (assoc :visit/treated? treated?)
    jurisdiction                                                              (assoc :visit/jurisdiction jurisdiction)
    status                                                                       (assoc :visit/status status)
    sample-number                                                                   (assoc :visit/sample-number sample-number)
    treatment-number                                                                  (assoc :visit/treatment-number treatment-number)))

(def ^:private visit-pull
  [:visit/id :visit/farm :visit/crop :visit/area :visit/label-rate :visit/claimed-dose
   :visit/approved-for-crop? :visit/near-water-source? :visit/buffer-compliant?
   :visit/sampled? :visit/treated?
   :visit/jurisdiction :visit/status :visit/sample-number :visit/treatment-number])

(defn- pull->visit [m]
  (when (:visit/id m)
    {:id (:visit/id m) :farm (:visit/farm m) :crop (:visit/crop m)
     :area (:visit/area m) :label-rate (:visit/label-rate m) :claimed-dose (:visit/claimed-dose m)
     :approved-for-crop? (boolean (:visit/approved-for-crop? m))
     :near-water-source? (boolean (:visit/near-water-source? m))
     :buffer-compliant? (boolean (:visit/buffer-compliant? m))
     :sampled? (boolean (:visit/sampled? m)) :treated? (boolean (:visit/treated? m))
     :jurisdiction (:visit/jurisdiction m) :status (:visit/status m)
     :sample-number (:visit/sample-number m) :treatment-number (:visit/treatment-number m)}))

(defrecord DatomicStore [conn]
  Store
  (visit [_ id]
    (pull->visit (d/pull (d/db conn) visit-pull [:visit/id id])))
  (all-visits [_]
    (->> (d/q '[:find [?id ...] :where [?e :visit/id ?id]] (d/db conn))
         (map #(pull->visit (d/pull (d/db conn) visit-pull [:visit/id %])))
         (sort-by :id)))
  (assessment-of [_ visit-id]
    (dec* (d/q '[:find ?p . :in $ ?vid
                :where [?a :assessment/visit-id ?vid] [?a :assessment/payload ?p]]
              (d/db conn) visit-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (sample-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :sample-record/seq ?s] [?e :sample-record/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (treatment-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :treatment-record/seq ?s] [?e :treatment-record/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-sample-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :sample-sequence/jurisdiction ?j] [?e :sample-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-treatment-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :treatment-sequence/jurisdiction ?j] [?e :treatment-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (visit-already-sampled? [s visit-id]
    (boolean (:sampled? (visit s visit-id))))
  (visit-already-treated? [s visit-id]
    (boolean (:treated? (visit s visit-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :visit/upsert
      (d/transact! conn [(visit->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/visit-id (first path) :assessment/payload (enc payload)}])

      :visit/mark-sampled
      (let [visit-id (first path)
            {:keys [result visit-patch]} (collect-sample! s visit-id)
            jurisdiction (:jurisdiction (visit s visit-id))
            next-n (inc (next-sample-sequence s jurisdiction))]
        (d/transact! conn
                     [(visit->tx (assoc visit-patch :id visit-id))
                      {:sample-sequence/jurisdiction jurisdiction :sample-sequence/next next-n}
                      {:sample-record/seq (count (sample-history s)) :sample-record/record (enc (get result "record"))}])
        result)

      :visit/mark-treated
      (let [visit-id (first path)
            {:keys [result visit-patch]} (apply-treatment! s visit-id)
            jurisdiction (:jurisdiction (visit s visit-id))
            next-n (inc (next-treatment-sequence s jurisdiction))]
        (d/transact! conn
                     [(visit->tx (assoc visit-patch :id visit-id))
                      {:treatment-sequence/jurisdiction jurisdiction :treatment-sequence/next next-n}
                      {:treatment-record/seq (count (treatment-history s)) :treatment-record/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-visits [s visits]
    (when (seq visits) (d/transact! conn (mapv visit->tx (vals visits)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:visits ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [visits]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-visits s visits))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo visit set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
