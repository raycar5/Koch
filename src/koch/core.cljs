(ns koch.core
  (:require [goog.object :as gobj]
            [reagent.core :as r]
            [clojure.core.async :refer [go chan timeout alts! close!]]
            [cljsjs.proptypes]))
(defn logger-middleware[{effects :effects :as context}]
  (assoc context :effects
   (reduce-kv
    (fn [map effect-key effect]
      (let [invocationv (volatile! 0)]
        (assoc
         map
         effect-key
         (fn [& args]
           (let [invocation (vswap! invocationv inc)
                 _ (print (str "started: " effect-key " i" invocation))
                 result (apply effect args)]
             (if (or (map? result) (nil? result))
               (do
                 (print (str "ended: " effect-key " i" invocation " state:" result))
                 result)
               (go
                 (let [result (<! result)]
                   (print (str "ended: " effect-key " i" invocation " state: " result))
                   result))))))))
    {} effects)))

(defn kupdate [value-or-function]
  (if (fn? value-or-function)
    (fn [effects & args]
      (fn [state]
        (apply value-or-function state args)))
    (fn [effects & args]
      (fn [state]
        value-or-function))))


(defn- deref-all [state]
   (reduce-kv (fn [m k v] (assoc m k @v)) {} state))

(defn- update-atom-map!
  [old-state new-state]
  (reduce-kv (fn [_ k2 new-value]
              (let [old-value (k2 old-state)]
               (if (not= @old-value new-value)
                 (reset! old-value new-value))
               nil))
             nil new-state)
  old-state)
(defn inject-state
  ([component & keys]
   (r/create-class
    {:context-types #js {:__koch-context js/PropTypes.object}
     :reagent-render (fn [props & children]
                       (let [this (r/current-component)
                             context (gobj/get (.-context this) "__koch-context")
                             state (:state context)
                             props (reduce (fn [props key] (assoc props key @(key state)))
                                           props keys)
                             effects (:effects context)]
                         [component (assoc props :effects effects) children]))}))
  ([component]
   (r/create-class
    {:context-types #js {:__koch-context js/PropTypes.object}
     :reagent-render (fn [props & children]
                       (let [this (r/current-component)
                             context (gobj/get (.-context this) "__koch-context")
                             state (deref-all (:state context))
                             effects (:effects context)]
                           [component (assoc props :state state :effects effects) children]))})))
(defn- generate-effects [effectsv state]
  (reduce-kv
   (fn [m effect-key effect-function-generator]
     (assoc m effect-key
            (fn [& args]
              (let [effect-function (apply effect-function-generator @effectsv args)
                    result (effect-function (deref-all state))]
                (cond
                  (nil? result) (deref-all state)
                  (map? result) (deref-all(update-atom-map! state result)) ;;if a value is returned just update the atom and return the state
                  :else (go      ;;if a channel is returned, treat it as a promise
                         (update-atom-map! state (<! result))
                         (close! result) ;; making sure the channel is a promiselike channel
                         (deref-all state)))))))
   {} @effectsv))
(defn- apply-middleware [middleware state effects]
  (reduce (fn [context middleware-function] (middleware-function context))
          {:state state :effects effects} middleware))
(defn provide-state
  ([component store]
   (let [atom (or (:atom-type store) r/atom)
         state (reduce-kv (fn [m k v] (assoc m k (atom v))) {} (:state store))
         effectsv (volatile! (:effects store))
         {:keys [store effects]} (apply-middleware (or (:middleware store) [])state (generate-effects effectsv state))]
     (vreset! effectsv effects)
     (r/create-class
      {:child-context-types #js {:__koch-context js/PropTypes.object}
       :context-types #js {:__koch-context js/PropTypes.object}
       :get-child-context #(this-as this
                             (let [parent-context (or (gobj/get (.-context this) "__koch-context") #js {})
                                   merged-state (merge (or (:state parent-context) {}) state)
                                   merged-effects (merge (or (:effects parent-context) {}) @effectsv)
                                   context #js {:__koch-context {:state merged-state :effects merged-effects}}]
                               context))
       :reagent-render (fn [props & children]
                         [component props children])}))))
