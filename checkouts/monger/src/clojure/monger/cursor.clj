;; This source code is dual-licensed under the Apache License, version
;; 2.0, and the Eclipse Public License, version 1.0.
;;
;; The APL v2.0:
;;
;; ----------------------------------------------------------------------------------
;; Copyright (c) 2011-2015 Michael S. Klishin, Alex Petrov, and the ClojureWerkz Team
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;; ----------------------------------------------------------------------------------
;;
;; The EPL v1.0:
;;
;; ----------------------------------------------------------------------------------
;; Copyright (c) 2011-2015 Michael S. Klishin, Alex Petrov, and the ClojureWerkz Team.
;; All rights reserved.
;;
;; This program and the accompanying materials are made available under the terms of
;; the Eclipse Public License Version 1.0,
;; which accompanies this distribution and is available at
;; http://www.eclipse.org/legal/epl-v10.html.
;; ----------------------------------------------------------------------------------

(ns monger.cursor
  "Helper-functions for dbCursor object: 
    * to initialize new cursor, 
    * for CRUD functionality of options of dbCursor"
  (:import  [com.mongodb DB DBCursor Bytes]
            [java.util List Map]
            [java.lang Integer]
            [clojure.lang Keyword])
  (:require [monger.core]
            [monger.conversion :refer [to-db-object from-db-object as-field-selector]]))

(defn ^DBCursor make-db-cursor 
  "initializes new db-cursor."
  ([^DB db ^String coll]
     (make-db-cursor db coll {} {}))
  ([^DB db ^String coll ^Map ref]
     (make-db-cursor db coll ref {}))
  ([^DB db ^String coll ^Map ref fields] 
    (.find
      (.getCollection db (name coll))
      (to-db-object ref)
      (as-field-selector fields)))) 

(def cursor-options {:awaitdata Bytes/QUERYOPTION_AWAITDATA
                     ;;:exhaust   Bytes/QUERYOPTION_EXHAUST - not human settable
                     :notimeout Bytes/QUERYOPTION_NOTIMEOUT
                     :oplogreplay Bytes/QUERYOPTION_OPLOGREPLAY
                     :partial Bytes/QUERYOPTION_PARTIAL
                     :slaveok Bytes/QUERYOPTION_SLAVEOK
                     :tailable Bytes/QUERYOPTION_TAILABLE})

(defn get-options
  "Returns map of cursor's options with current state."
  [^DBCursor db-cur]
  (into {}
    (for [[opt option-mask] cursor-options]
      [opt (< 0 (bit-and (.getOptions db-cur) option-mask))])))

(defn add-option!
  [^DBCursor db-cur ^String opt]
  (.addOption db-cur (get cursor-options (keyword opt) 0)))

(defn remove-option!
  [^DBCursor db-cur ^String opt]
  (.setOptions db-cur (bit-and-not (.getOptions db-cur)
                                   (get cursor-options (keyword opt) 0))))

(defmulti add-options (fn [db-cur opts] (class opts)))
(defmethod add-options Map [^DBCursor db-cur options]
  "Changes options by using map of settings, which key specifies name of settings 
  and boolean value specifies new state of the setting.
  usage: 
    (add-options db-cur {:notimeout true, :tailable false})
  returns: 
    ^DBCursor object."
  (doseq [[opt value] (seq options)]
    (if (= true value)
      (add-option! db-cur opt)
      (remove-option! db-cur opt)))
  db-cur)

(defmethod add-options List [^DBCursor db-cur options]
  "Takes list of options and activates these options
  usage:
    (add-options db-cur [:notimeout :tailable])
  returns:
    ^DBCursor object"
  (doseq [opt (seq options)] 
    (add-option! db-cur opt))
  db-cur)

(defmethod add-options Integer [^DBCursor db-cur, option]
  "Takes com.mongodb.Byte value and adds it to current settings.
  usage:
    (add-options db-cur com.mongodb.Bytes/QUERYOPTION_NOTIMEOUT)
  returns:
    ^DBCursor object"
  (.addOption db-cur option)
  db-cur)

(defmethod add-options Keyword [^DBCursor db-cur, option]
  "Takes just one keyword as name of settings and applies it to the db-cursor.
  usage:
    (add-options db-cur :notimeout)
  returns:
    ^DBCursor object"
  (add-option! db-cur option)
  db-cur)

(defmethod add-options :default [^DBCursor db-cur, options]
  "Using add-options with not supported type of options just passes unchanged cursor"
  db-cur)

(defn ^DBCursor reset-options
  "Resets cursor options to default value and returns cursor"
  [^DBCursor db-cur]
  (.resetOptions db-cur)
  db-cur)

(defmulti format-as (fn [db-cur as] as))

(defmethod format-as :map [db-cur as]
  (map #(from-db-object %1 true) db-cur))

(defmethod format-as :seq [db-cur as]
  (seq db-cur))

(defmethod format-as :default [db-cur as]
  db-cur)

