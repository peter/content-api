(ns versioned.model-includes.audited-model
  (:require [versioned.util.date :as d]))

(defn audit-create-callback [doc options]
  (assoc doc :created_at (d/now)))

(defn audit-update-callback [doc options]
  (assoc doc :updated_at (d/now)))

(def audited-schema {
  :type "object"
  :properties {
    :created_at {:type "string" :format "date-time" :meta {:api_writable false :versioned false}}
    :created_by {:type "string" :meta {:api_writable false :versioned false}}
    :updated_at {:type "string" :format "date-time" :meta {:api_writable false :versioned false :change-tracking false}}
    :updated_by {:type "string" :meta {:api_writable false :versioned false :change-tracking false}}
  }
  :required [:created_at :created_by]
})

(def audited-callbacks {
  :create {
    :before [audit-create-callback]
  }
  :update {
    :before [audit-update-callback]
  }
})

(defn audited-spec [& options] {
    :schema audited-schema
    :callbacks audited-callbacks
})
