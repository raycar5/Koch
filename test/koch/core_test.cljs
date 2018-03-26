(ns koch.core-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests async]]
            [clojure.core.async :refer [go timeout <!]]
            [koch.core :as k]))

(deftest update-returns-map
  (let [state {:foo "bar"}
        update (k/kupdate state)]
    (is (= state ((update))))))

(deftest update-passes-the-state
  (let [state {:foo "bar"}
        update (k/kupdate (fn [state] state))]
    (is (= state ((update)state)))))

(deftest update-passes-the-state-and-arguments
  (let [state {:foo "bar"}
        arg "argument"
        update (k/kupdate (fn [state arg] (assoc state :arg arg)))]
    (is (= (assoc state :arg arg) ((update nil arg) state)))))

(deftest synchronous-effects
  (let [state {:count (atom 0)}
        effectsv (volatile! {:inc (k/kupdate (fn [{:keys [count]}] {:count (inc count)}))})]
    (vreset! effectsv (k/generate-effects effectsv state))
    ((:inc @effectsv))
    (is (= @(:count state) 1))))

(deftest asynchronous-effects
  (async done
         (let [state {:count (atom 0)}
               effectsv (volatile!
                         {:inc
                          (k/kupdate
                           (fn [{:keys [count]}]
                             (go
                               (<! (timeout 100))
                               {:count (inc count)})))})]
           (vreset! effectsv (k/generate-effects effectsv state))
           (go
             (<! ((:inc @effectsv)))
             (is (= @(:count state) 1))
             (done)))))
