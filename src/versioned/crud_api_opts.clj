(ns versioned.crud-api-opts
  (:require [versioned.util.core :as u]
            [versioned.model-support :refer [id-attribute]]))

(defn- pagination [request]
  (->> (select-keys (get-in request [:params]) [:page :per-page])
       (u/map-values u/safe-parse-int)
       (u/compact)))

(defn- sort [model-spec request]
  (if (= (id-attribute model-spec) :id)
    {:sort (array-map :id -1)}
    {}))

(defn list-opts [model-spec request]
  (merge (pagination request)
         (sort model-spec request)))

(defn get-opts [request]
  (select-keys (:params request) [:relationships :version :published]))
