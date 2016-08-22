(ns monger.test.indexing-test
  (:import  org.bson.types.ObjectId
            java.util.Date)
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            monger.joda-time
            [clojure.test :refer :all]
            [clj-time.core :refer [now seconds ago from-now]]))

(let [conn (mg/connect)
      db   (mg/get-db conn "monger-test")]
  (deftest ^{:indexing true} test-creating-and-dropping-indexes
    (let [collection "libraries"]
      (mc/drop-indexes db collection)
      (mc/create-index db collection {"language" 1})
      (is (= "language_1"
             (:name (second (mc/indexes-on db collection)))))
      (mc/drop-indexes db collection)
      (is (nil? (second (mc/indexes-on db collection))))
      (mc/ensure-index db collection (array-map "language" 1) {:unique true})
      (is (= "language_1"
             (:name (second (mc/indexes-on db collection)))))
      (mc/drop-indexes db collection)
      (mc/ensure-index db collection (array-map "language" 1))
      (mc/drop-indexes db collection)
      (mc/ensure-index db collection (array-map "language" 1) {:unique true})
      (mc/drop-indexes db collection)
      (mc/ensure-index db collection (array-map "language" 1) "index-name" true)
      (mc/drop-indexes db collection)))

  (deftest ^{:indexing true :time-consuming true} test-ttl-collections
    (let [coll  "recent_events"
          ttl   15
          sleep 65]
      (mc/remove db coll)
      (mc/drop-indexes db coll)
      (mc/ensure-index db coll (array-map :created-at 1) {:expireAfterSeconds ttl})
      (dotimes [i 100]
        (mc/insert db coll {:type "signup" :created-at (-> i seconds ago) :i i}))
      (dotimes [i 100]
        (mc/insert db coll {:type "signup" :created-at (-> i seconds from-now) :i i}))
      (is (= 200 (mc/count db coll {:type "signup"})))
      ;; sleep for > 60 seconds. MongoDB seems to run TTLMonitor once per minute, according to
      ;; the log.
      (println (format "Now sleeping for %d seconds to test TTL collections!" sleep))
      (Thread/sleep (* sleep 1000))
      (println (format "Documents in the TTL collection: %d" (mc/count db coll {:type "signup"})))
      (is (< (mc/count db coll {:type "signup"}) 100))
      (mc/remove db coll))))
