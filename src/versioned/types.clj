(ns versioned.types
  (:require [schema.core :as s]
            [versioned.util.core :as u]))

(def Map {s/Keyword s/Any})
(def Nil (s/pred nil? 'nil?))
(def Function (s/pred fn? 'fn?))
(def StrOrKeyword (s/cond-pre s/Str s/Keyword))
(def Coll (s/pred coll? 'coll?))

(def AttributeList (s/constrained Coll #(every? keyword? %)))

(def PosInt (s/pred u/positive-int? 'positive-int?))

(def ID (s/cond-pre s/Str PosInt))

(def LogLevel (s/enum "info" "debug"))

(def Request Map)

(def Attributes Map)
(def AttributeKeys #{s/Keyword})

(def Changelog Map)
(def Action (s/pred #{:create :update :delete} 'changelog-action?))
(def Email s/Str)

(def JsonApiAttributes {
                        :id s/Str
                        (s/optional-key :type) s/Str
                        :attributes Map})
(def JsonApiData {
                  :data [JsonApiAttributes]})
(def JsonApiResource (merge JsonApiAttributes {
                                                (s/optional-key :relationships) {s/Keyword JsonApiData}}))
(def JsonApiError {
                   :type s/Str
                   (s/optional-key :message) s/Str
                   s/Keyword s/Any})
(def JsonApiResponse {
                      :status s/Int
                      (s/optional-key :body) Map})

(def JsonApiErrorResponse {:body {:errors [JsonApiError]} :status s/Int})
(def JsonApiDataResponse {:body {:data Coll} :status s/Int})

(def crud-actions [:list :get :create :update :delete])
(defn valid-routes? [routes]
  (empty? (clojure.set/difference (set routes)
                                  (set crud-actions))))

(declare Schema)
(def SchemaValue (s/cond-pre s/Str s/Num Nil s/Bool Coll))
(def SchemaType (s/enum "string" "number" "integer" "null" "boolean" "array" "object"))
(def Schema {
             (s/optional-key :type) (s/cond-pre SchemaType [SchemaType])
             (s/optional-key :properties) {s/Keyword (s/recursive #'Schema)}
             (s/optional-key :additionalProperties) s/Bool
             (s/optional-key :required) [StrOrKeyword]
             (s/optional-key :items) (s/recursive #'Schema)
             (s/optional-key :enum) [SchemaValue]
             s/Keyword s/Any})

; TODO: this spec is a duplicate of the JSON schema in model_spec.clj
(def Routes (s/pred valid-routes? 'valid-routes?))
(def Model {
            :type s/Keyword
            :schema Schema
            (s/optional-key :callbacks) Map
            (s/optional-key :relationships) Map
            (s/optional-key :indexes) [Map]
            (s/optional-key :routes) Routes
            s/Keyword s/Any})


(def Models {s/Keyword Model})

(def DB-Schema (s/pred #(instance? com.mongodb.DB %) 'mongodb-database?))
(def DB-Conn (s/pred #(instance? com.mongodb.MongoClient %) 'mongodb-conn?))
(def WriteResult (s/pred #(instance? com.mongodb.WriteResult %) 'write-result?))
(def DB-IndexOptions {
                      (s/optional-key :unique) s/Bool
                      (s/optional-key :name) s/Str})

(def Database {:db DB-Schema :conn DB-Conn s/Keyword s/Any})

(def Route Map)

(def Handler Function)

(def ModelSpecPath (s/constrained String #(re-matches #"^([\w.-]+)/([\w-]+)$" %))) ; "versioned.models.users/spec"

(def ModelsConfig (s/constrained
                    {s/Keyword (s/conditional map? Model :else ModelSpecPath)}
                    (fn [m]
                      (every? (fn [[k v]]
                                (or (string? v)
                                    (= k (:type v))))
                              (seq m)))))

(def Config {
             :models ModelsConfig
             s/Keyword s/Any})


(def App {
          :config Config
          :models Models
          :swagger Map
          :routes [Route]
          s/Keyword s/Any})
