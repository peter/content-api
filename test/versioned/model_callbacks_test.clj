(ns versioned.model-callbacks-test
  (:require [clojure.test :refer :all]
            [versioned.model-callbacks :as model-callbacks]))

(defn foo [] "foo")
(defn bar [] "bar")

(deftest normalize-callbacks_does-not-touch-callbacks-without-save
  (is (= (model-callbacks/normalize-callbacks {:create {:before [foo]} :update {:before [bar]}})
         {:create {:before [foo]} :update {:before [bar]}})))

(deftest normalize-callbacks_merges-save-callbacks-with-update-create-callbacks
  (is (= (model-callbacks/normalize-callbacks {:save {:before [foo]} :update {:before [bar]}})
         {:create {:before [foo]} :update {:before [foo bar]}})))

(deftest invoke-callbacks_invokes-callbacks-in-order-with-doc-options-and-returns-the-resulting-doc
  (let [callbacks [
          (fn [doc options]
            (assoc (update-in doc [:callback_trail] concat [:first]) :options-received (= (:app options) "the-app")))
          (fn [doc options]
            (assoc (update-in doc [:callback_trail] concat [:second]) :new-attribute true :title "foobar changed"))
          ]
        doc {:title "foobar"}
        options {:app "the-app"}]
      (is (= (model-callbacks/invoke-callbacks callbacks options doc)
             {:title "foobar changed" :new-attribute true :options-received true :callback_trail [:first :second]}))))

(deftest with-callbacks_wraps-a-model-fn-app-model-spec-doc-in-a-function-that-invokes-callbacks-before-after
  (let [model-fn (fn [app model-spec doc] (update-in doc [:callback_trail] concat [:model]))
        action :create
        callbacks {:create {
          :before [(fn [doc options]
                     (let [options-received (and (= (:app options) {:database "the-database"})
                                                 (= (:action options) :create))]
                       (assoc (update-in doc [:callback_trail] concat [:first]) :options-received options-received)))]
          :after  [(fn [doc options]
                     (assoc (update-in doc [:callback_trail] concat [:second]) :new-attribute true :title "foobar changed"))]
          }}
        model-fn-with-callbacks (model-callbacks/with-callbacks model-fn action)
        app {:database "the-database"}
        model-spec {:type :pages :callbacks callbacks}
        doc {:title "foobar"}]
      (is (= (model-fn-with-callbacks app model-spec doc)
             {:title "foobar changed" :new-attribute true :options-received true :callback_trail [:first :model :second]}))))
