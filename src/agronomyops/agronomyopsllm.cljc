(ns agronomyops.agronomyopsllm
  "AgronomyOps-LLM client -- the *contained intelligence node* for the
  community-agronomy actor.

  It normalizes visit intake, drafts a per-jurisdiction agrochemical-
  registration/water-buffer-zone evidence checklist, drafts the
  sample-collection action, and drafts the treatment-application
  action. CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record or a real sample/treatment. Every output is
  censored downstream by `agronomyops.governor` before anything
  touches the SSoT, and `:sample/collect`/`:treatment/apply` proposals
  NEVER auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/collect-sample | :actuation/apply-treatment | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [agronomyops.facts :as facts]
            [agronomyops.registry :as registry]
            [agronomyops.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the crop, area or jurisdiction. High confidence, low
  stakes."
  [_db {:keys [patch]}]
  {:summary    (str "訪問記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :visit/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-jurisdiction
  "Per-jurisdiction agrochemical-registration/water-buffer-zone
  evidence checklist draft. `:no-spec?` injects the failure mode we
  must defend against: proposing a checklist for a jurisdiction with
  NO official spec-basis in `agronomyops.facts` -- the Agronomy
  Governor must reject this (never invent a jurisdiction's
  requirements)."
  [db {:keys [subject no-spec?]}]
  (let [v (store/visit db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction v))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "agronomyops.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- propose-sample
  "Draft the actual SAMPLE-COLLECTION action -- collecting a real
  soil/crop sample in the field. ALWAYS `:stake :actuation/collect-
  sample` -- this is a REAL-WORLD act (a robot physically samples the
  field), never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`agronomyops.phase`); the governor also always escalates on
  `:actuation/collect-sample`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [v (store/visit db subject)]
    {:summary    (str subject " 向けサンプリング提案"
                      (when v (str " (farm=" (:farm v) ")")))
     :rationale  (if v
                   (str "crop=" (:crop v) " jurisdiction=" (:jurisdiction v))
                   "visitが見つかりません")
     :cites      (if v [subject] [])
     :effect     :visit/mark-sampled
     :value      {:visit-id subject}
     :stake      :actuation/collect-sample
     :confidence (if v 0.9 0.3)}))

(defn- propose-treatment
  "Draft the actual TREATMENT-APPLICATION action -- applying a real
  targeted treatment (agrochemical dispersal in the field). ALWAYS
  `:stake :actuation/apply-treatment` -- this is a REAL-WORLD act (real
  agrochemical leaves the sprayer, water-buffer obligations apply),
  never a draft the actor may auto-run. See README `Actuation`: no
  phase ever adds this op to a phase's `:auto` set (`agronomyops.
  phase`); the governor also always escalates on `:actuation/apply-
  treatment`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [v (store/visit db subject)
        dose-ok? (and v (registry/dose-matches-claim? v))
        approved-ok? (and v (:approved-for-crop? v))
        buffer-ok? (and v (or (not (:near-water-source? v)) (:buffer-compliant? v)))]
    {:summary    (str subject " 向け防除処理提案"
                      (when v (str " (farm=" (:farm v) ")")))
     :rationale  (if v
                   (str "claimed-dose=" (:claimed-dose v)
                        " independent-recompute=" (registry/compute-dose v)
                        " approved-for-crop?=" approved-ok?
                        " buffer-ok?=" buffer-ok?)
                   "visitが見つかりません")
     :cites      (if v [subject] [])
     :effect     :visit/mark-treated
     :value      {:visit-id subject}
     :stake      :actuation/apply-treatment
     :confidence (if (and dose-ok? approved-ok? buffer-ok?) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :visit/intake            (normalize-intake db request)
    :jurisdiction/assess          (assess-jurisdiction db request)
    :sample/collect                  (propose-sample db request)
    :treatment/apply                     (propose-treatment db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは地域農業支援業者のサンプリング・防除エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:visit/upsert|:assessment/set|:visit/mark-sampled|"
       ":visit/mark-treated) "
       ":stake(:actuation/collect-sample か :actuation/apply-treatment か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"
       "散布量や資材の作物適用承認、水源緩衝帯の遵守状況を偽って報告してはいけません。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :jurisdiction/assess    {:visit (store/visit st subject)}
    :sample/collect         {:visit (store/visit st subject)}
    :treatment/apply        {:visit (store/visit st subject)}
    {:visit (store/visit st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Agronomy Governor
  escalates/holds -- an LLM hiccup can never auto-collect a sample or
  auto-apply a treatment."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :agronomyopsllm-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
