(ns check-type
    (:require [clojure.pprint :refer [pprint]]))

; configurations you can change
(def check-default
    (atom {:eval-lazy :peek
           :combine-keys [number?]
           :combine-numbers true
           :preserve-vals [keyword?]
           :levels 5}))

(defn surround
    "Surround a list of objects with a kind of collection
     while avoid iterating map entries.
     Intended for internal use.
     (surround [] '(1 2 3)) -> [1 2 3],
     (surround [] {:a 1 :b 3}) -> [{:a 1 :b 3}]"
    [coll objects]
    (if (map? objects)
        (conj coll objects)
        (into coll objects)))

; combine and combine-maps are mutually dependent
(declare combine)

(defn combine-maps
    "Handle the special case for (combine) where the list
     is made of maps.
     [{:a 1 :b 2} {:a 3 :b 2}] -> {:a [:union (1 3)] :b 2}"
    [maps]
    (into {}
          (for [[k kv-pairs] (group-by first (apply concat maps))
                :let [uniq-types (combine (map second kv-pairs))]]
              (if (= (count uniq-types) 1)
                  [k (first uniq-types)]
                  [k [:union uniq-types]]))))

(defn combine
    "Collect type values within lists and vectors if possible.
     Intended for internal use."
    [types]
    (let [coll-type (fn [object]
                        (cond
                            (seq? object) :seq
                            (set? object) :set
                            (vector? object) :vec
                            (map? object) :map
                            :else :none))
          rm-unions (fn [object]
                        (if (and (vector? object)
                                 (= (first object) :union))
                            (second object)
                            ; unions contain a list of objects,
                            ; so wrap non-union objects in a list
                            ; for easier map and concat
                            [object]))
          no-unions (apply concat (map rm-unions types))
          groups (group-by coll-type no-unions)]
        (concat
            ; filter out the nil for :none in (case)
            (filter some?
                (for [[coll subtypes] groups]
                    (case coll
                        :seq (combine (distinct (apply concat subtypes)))
                        :set (surround #{} (combine (apply clojure.set/union subtypes)))
                        :vec (surround [] (combine (distinct (apply concat subtypes))))
                        :map (combine-maps types)
                        :none nil)))
            ; concat non-collection values at end to
            ; prevent single values from being wrapped in an
            ; extra list by (for)
            (distinct (:none groups)))))

(defn check
    "Returns the type of the given object.
     When configurations are given, they're merged onto
     the default configurations."
    ([object] (check object {}))
    ([object call-config]
     (let [config (merge @check-default call-config)
           dec-check
           (fn [object]
               (check object (update config :levels dec)))]
         (cond
             (= (:levels config) 0) (type object)

             (instance? clojure.lang.LazySeq object)
             (case (:eval-lazy config)
                 :peek
                 (surround '() [(dec-check (first object))])

                 :all
                 (surround '() (combine (map dec-check object)))

                 ; default to not eval in case it's infinite
                 (type object))

             (vector? object)
             (surround [] (combine (map dec-check object)))

             (set? object)
             (surround #{} (combine (map dec-check object)))

             (map? object)
             (cond
                 (empty? object) {}

                 (some #(every? % (keys object))
                       (:combine-keys config))
                 (combine-maps (for [[k v] object]
                                   {(dec-check k)
                                    (dec-check v)}))

                 :else
                 (into {} (for [[k v] object]
                              [k (dec-check v)])))

             ; seq? at the end as a catch-all for different lists
             (seq? object)
             (surround '() (combine (map dec-check object)))

             (and (number? object) (:combine-numbers config))
             :number

             (some #(% object) (:preserve-vals config))
             object

             :else (type object)))))

(defn pcheck
    "pprint check"
    ([obj] (pprint (check obj)))
    ([obj config] (pprint (check obj config))))
