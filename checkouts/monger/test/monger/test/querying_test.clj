(ns monger.test.querying-test
  (:refer-clojure :exclude [select find sort])
  (:import  [com.mongodb WriteResult WriteConcern DBObject ReadPreference]
            org.bson.types.ObjectId
            java.util.Date)
  (:require [monger.core :as mg]
            [monger.collection  :as mc]
            monger.joda-time
            [monger.result      :as mgres]
            [clojure.test :refer :all]
            [monger.conversion :refer :all]
            [monger.query :refer :all]
            [monger.operators :refer :all]
            [clj-time.core :refer [date-time]]))

(let [conn (mg/connect)
      db   (mg/get-db conn "monger-test")]

  (defn purge-collections
    [f]
    (mc/remove db "docs")
    (mc/remove db "things")
    (mc/remove db "locations")
    (mc/remove db "querying_docs")
    (f)
    (mc/remove db "docs")
    (mc/remove db "things")
    (mc/remove db "locations")
    (mc/remove db "querying_docs"))

  (use-fixtures :each purge-collections)

  ;;
  ;; monger.collection/* finders ("low-level API")
  ;;

  ;; by ObjectId

  (deftest query-full-document-by-object-id
    (let [coll "querying_docs"
          oid  (ObjectId.)
          doc  { :_id oid :title "Introducing Monger" }]
      (mc/insert db coll doc)
      (is (= doc (mc/find-map-by-id db coll oid)))
      (is (= doc (mc/find-one-as-map db coll { :_id oid })))))


  ;; exact match over string field

  (deftest query-full-document-using-exact-matching-over-string-field
    (let [coll "querying_docs"
          doc  { :title "monger" :language "Clojure" :_id (ObjectId.) }]
      (mc/insert db coll doc)
      (is (= [doc] (mc/find-maps db coll { :title "monger" })))
      (is (= doc (from-db-object (first (mc/find db coll { :title "monger" })) true)))))


  ;; exact match over string field with limit

  (deftest query-full-document-using-exact-matching-over-string-with-field-with-limit
    (let [coll "querying_docs"
          doc1  { :title "monger"  :language "Clojure" :_id (ObjectId.) }
          doc2  { :title "langohr" :language "Clojure" :_id (ObjectId.) }
          doc3  { :title "netty"   :language "Java" :_id (ObjectId.) }
          _     (mc/insert-batch db coll [doc1 doc2 doc3])
          result (with-collection db coll
                   (find { :title "monger" })
                   (fields [:title, :language, :_id])
                   (skip 0)
                   (limit 1))]
      (is (= 1 (count result)))
      (is (= [doc1] result))))


  (deftest query-full-document-using-exact-matching-over-string-field-with-limit-and-offset
    (let [coll "querying_docs"
          doc1  { :title "lucene"    :language "Java" :_id (ObjectId.) }
          doc2  { :title "joda-time" :language "Java" :_id (ObjectId.) }
          doc3  { :title "netty"     :language "Java" :_id (ObjectId.) }
          _     (mc/insert-batch db coll [doc1 doc2 doc3])
          result (with-collection db coll
                   (find { :language "Java" })
                   (skip 1)
                   (limit 2)
                   (sort { :title 1 }))]
      (is (= 2 (count result)))
      (is (= [doc1 doc3] result))))

  (deftest query-with-sorting-on-multiple-fields
    (let [coll "querying_docs"
          doc1  { :a 1 :b 2 :c 3 :text "Whatever" :_id (ObjectId.) }
          doc2  { :a 1 :b 1 :c 4 :text "Blah " :_id (ObjectId.) }
          doc3  { :a 10 :b 3 :c 1 :text "Abc"  :_id (ObjectId.) }
          doc4  { :a 10 :b 3 :c 3 :text "Abc"  :_id (ObjectId.) }
          _     (mc/insert-batch db coll [doc1 doc2 doc3 doc4])
          result1 (with-collection db coll
                    (find {})
                    (limit 2)
                    (fields [:a :b :c :text])
                    (sort (sorted-map :a 1 :b 1 :text -1)))
          result2 (with-collection db coll
                    (find {})
                    (limit 2)
                    (fields [:a :b :c :text])
                    (sort (array-map :c 1 :text -1)))
          result3 (with-collection db coll
                    (find {})
                    (limit 2)
                    (fields [:a :b :c :text])
                    (sort (array-map :c -1 :text 1)))]
      (is (= [doc2 doc1] result1))
      (is (= [doc3 doc1] result2))
      (is (= [doc2 doc4] result3))))


  ;; < ($lt), <= ($lte), > ($gt), >= ($gte)

  (deftest query-using-dsl-and-$lt-operator-with-integers
    (let [coll "querying_docs"
          doc1 { :language "Clojure" :_id (ObjectId.) :inception_year 2006 }
          doc2 { :language "Java"    :_id (ObjectId.) :inception_year 1992 }
          doc3 { :language "Scala"   :_id (ObjectId.) :inception_year 2003 }
          _      (mc/insert-batch db coll [doc1 doc2])
          lt-result (with-collection db coll
                      (find { :inception_year { $lt 2000 } })
                      (limit 2))]
      (is (= [doc2] (vec lt-result)))))


  (deftest query-using-dsl-and-$lt-operator-with-dates
    (let [coll "querying_docs"
          ;; these rely on monger.joda-time being loaded. MK.
          doc1 { :language "Clojure" :_id (ObjectId.) :inception_year (date-time 2006 1 1) }
          doc2 { :language "Java"    :_id (ObjectId.) :inception_year (date-time 1992 1 2) }
          doc3 { :language "Scala"   :_id (ObjectId.) :inception_year (date-time 2003 3 3) }
          _    (mc/insert-batch db coll [doc1 doc2])
          lt-result (with-collection db coll
                      (find { :inception_year { $lt (date-time 2000 1 2) } })
                      (limit 2))]
      (is (= (map :_id [doc2])
             (map :_id (vec lt-result))))))

  (deftest query-using-both-$lte-and-$gte-operators-with-dates
    (let [coll "querying_docs"
          ;; these rely on monger.joda-time being loaded. MK.
          doc1 { :language "Clojure" :_id (ObjectId.) :inception_year (date-time 2006 1 1) }
          doc2 { :language "Java"    :_id (ObjectId.) :inception_year (date-time 1992 1 2) }
          doc3 { :language "Scala"   :_id (ObjectId.) :inception_year (date-time 2003 3 3) }
          _    (mc/insert-batch db coll [doc1 doc2 doc3])
          lt-result (with-collection db coll
                      (find { :inception_year { $gt (date-time 2000 1 2) $lte (date-time 2007 2 2) } })
                      (sort { :inception_year 1 }))]
      (is (= (map :_id [doc3 doc1])
             (map :_id (vec lt-result))))))


  (deftest query-using-$gt-$lt-$gte-$lte-operators-as-strings
    (let [coll "querying_docs"
          doc1 { :language "Clojure" :_id (ObjectId.) :inception_year 2006 }
          doc2 { :language "Java"    :_id (ObjectId.) :inception_year 1992 }
          doc3 { :language "Scala"   :_id (ObjectId.) :inception_year 2003 }
          _    (mc/insert-batch db coll [doc1 doc2 doc3])]
      (are [doc, result]
        (= doc, result)
        (doc2 (with-collection db coll
                (find { :inception_year { "$lt"  2000 } })))
        (doc2 (with-collection db coll
                (find { :inception_year { "$lte" 1992 } })))
        (doc1 (with-collection db coll
                (find { :inception_year { "$gt"  2002 } })
                (limit 1)
                (sort { :inception_year -1 })))
        (doc1 (with-collection db coll
                (find { :inception_year { "$gte" 2006 } }))))))


  (deftest query-using-$gt-$lt-$gte-$lte-operators-using-dsl-composition
    (let [coll "querying_docs"
          doc1 { :language "Clojure" :_id (ObjectId.) :inception_year 2006 }
          doc2 { :language "Java"    :_id (ObjectId.) :inception_year 1992 }
          doc3 { :language "Scala"   :_id (ObjectId.) :inception_year 2003 }
          srt  (-> {}
                   (limit 1)
                   (sort { :inception_year -1 }))
          _    (mc/insert-batch db coll [doc1 doc2 doc3])]
      (is (= [doc1] (with-collection db coll
                      (find { :inception_year { "$gt"  2002 } })
                      (merge srt))))))


  ;; $all

  (deftest query-with-using-$all
    (let [coll "querying_docs"
          doc1 { :_id (ObjectId.) :title "Clojure" :tags ["functional" "homoiconic" "syntax-oriented" "dsls" "concurrency features" "jvm"] }
          doc2 { :_id (ObjectId.) :title "Java"    :tags ["object-oriented" "jvm"] }
          doc3 { :_id (ObjectId.) :title "Scala"   :tags ["functional" "object-oriented" "dsls" "concurrency features" "jvm"] }
          -    (mc/insert-batch db coll [doc1 doc2 doc3])
          result1 (with-collection db coll
                    (find { :tags { "$all" ["functional" "jvm" "homoiconic"] } }))
          result2 (with-collection db coll
                    (find { :tags { "$all" ["functional" "native" "homoiconic"] } }))
          result3 (with-collection db coll
                    (find { :tags { "$all" ["functional" "jvm" "dsls"] } })
                    (sort { :title 1 }))]
      (is (= [doc1] result1))
      (is (empty? result2))
      (is (= 2 (count result3)))
      (is (= doc1 (first result3)))))


  ;; $exists

  (deftest query-with-find-one-as-map-using-$exists
    (let [coll "querying_docs"
          doc1 { :_id (ObjectId.) :published-by "Jill The Blogger" :draft false :title "X announces another Y" }
          doc2 { :_id (ObjectId.) :draft true :title "Z announces a Y competitor" }
          _    (mc/insert-batch db coll [doc1 doc2])
          result1 (mc/find-one-as-map db coll { :published-by { "$exists" true } })
          result2 (mc/find-one-as-map db coll { :published-by { "$exists" false } })]
      (is (= doc1 result1))
      (is (= doc2 result2))))

  ;; $mod

  (deftest query-with-find-one-as-map-using-$mod
    (let [coll "querying_docs"
          doc1 { :_id (ObjectId.) :counter 25 }
          doc2 { :_id (ObjectId.) :counter 32 }
          doc3 { :_id (ObjectId.) :counter 63 }
          _    (mc/insert-batch db coll [doc1 doc2 doc3])
          result1 (mc/find-one-as-map db coll { :counter { "$mod" [10, 5] } })
          result2 (mc/find-one-as-map db coll { :counter { "$mod" [10, 2] } })
          result3 (mc/find-one-as-map db coll { :counter { "$mod" [11, 1] } })]
      (is (= doc1 result1))
      (is (= doc2 result2))
      (is (empty? result3))))


  ;; $ne

  (deftest query-with-find-one-as-map-using-$ne
    (let [coll "querying_docs"
          doc1 { :_id (ObjectId.) :counter 25 }
          doc2 { :_id (ObjectId.) :counter 32 }
          _    (mc/insert-batch db coll [doc1 doc2])
          result1 (mc/find-one-as-map db coll { :counter { "$ne" 25 } })
          result2 (mc/find-one-as-map db coll { :counter { "$ne" 32 } })]
      (is (= doc2 result1))
      (is (= doc1 result2))))

  ;;
  ;; monger.query DSL features
  ;;

  ;; pagination
  (deftest query-using-pagination-dsl
    (let [coll "querying_docs"
          doc1 { :_id (ObjectId.) :title "Clojure" :tags ["functional" "homoiconic" "syntax-oriented" "dsls" "concurrency features" "jvm"] }
          doc2 { :_id (ObjectId.) :title "Java"    :tags ["object-oriented" "jvm"] }
          doc3 { :_id (ObjectId.) :title "Scala"   :tags ["functional" "object-oriented" "dsls" "concurrency features" "jvm"] }
          doc4 { :_id (ObjectId.) :title "Ruby"    :tags ["dynamic" "object-oriented" "dsls" "jvm"] }
          doc5 { :_id (ObjectId.) :title "Groovy"  :tags ["dynamic" "object-oriented" "dsls" "jvm"] }
          doc6 { :_id (ObjectId.) :title "OCaml"   :tags ["functional" "static" "dsls"] }
          doc7 { :_id (ObjectId.) :title "Haskell" :tags ["functional" "static" "dsls" "concurrency features"] }
          -    (mc/insert-batch db coll [doc1 doc2 doc3 doc4 doc5 doc6 doc7])
          result1 (with-collection db coll
                    (find {})
                    (paginate :page 1 :per-page 3)
                    (sort { :title 1 })
                    (read-preference (ReadPreference/primary))
                    (options com.mongodb.Bytes/QUERYOPTION_NOTIMEOUT))
          result2 (with-collection db coll
                    (find {})
                    (paginate :page 2 :per-page 3)
                    (sort { :title 1 }))
          result3 (with-collection db coll
                    (find {})
                    (paginate :page 3 :per-page 3)
                    (sort { :title 1 }))
          result4 (with-collection db coll
                    (find {})
                    (paginate :page 10 :per-page 3)
                    (sort { :title 1 }))]
      (is (= [doc1 doc5 doc7] result1))
      (is (= [doc2 doc6 doc4] result2))
      (is (= [doc3] result3))
      (is (empty? result4))))


  (deftest combined-querying-dsl-example1
    (let [coll "querying_docs"
          ma-doc { :_id (ObjectId.) :name "Massachusetts" :iso "MA" :population 6547629  :joined_in 1788 :capital "Boston" }
          de-doc { :_id (ObjectId.) :name "Delaware"      :iso "DE" :population 897934   :joined_in 1787 :capital "Dover"  }
          ny-doc { :_id (ObjectId.) :name "New York"      :iso "NY" :population 19378102 :joined_in 1788 :capital "Albany" }
          ca-doc { :_id (ObjectId.) :name "California"    :iso "CA" :population 37253956 :joined_in 1850 :capital "Sacramento" }
          tx-doc { :_id (ObjectId.) :name "Texas"         :iso "TX" :population 25145561 :joined_in 1845 :capital "Austin" }
          top3               (partial-query (limit 3))
          by-population-desc (partial-query (sort { :population -1 }))
          _                  (mc/insert-batch db coll [ma-doc de-doc ny-doc ca-doc tx-doc])
          result             (with-collection db coll
                               (find {})
                               (merge top3)
                               (merge by-population-desc))]
      (is (= result [ca-doc tx-doc ny-doc]))))

  (deftest combined-querying-dsl-example2
    (let [coll "querying_docs"
          ma-doc { :_id (ObjectId.) :name "Massachusetts" :iso "MA" :population 6547629  :joined_in 1788 :capital "Boston" }
          de-doc { :_id (ObjectId.) :name "Delaware"      :iso "DE" :population 897934   :joined_in 1787 :capital "Dover"  }
          ny-doc { :_id (ObjectId.) :name "New York"      :iso "NY" :population 19378102 :joined_in 1788 :capital "Albany" }
          ca-doc { :_id (ObjectId.) :name "California"    :iso "CA" :population 37253956 :joined_in 1850 :capital "Sacramento" }
          tx-doc { :_id (ObjectId.) :name "Texas"         :iso "TX" :population 25145561 :joined_in 1845 :capital "Austin" }
          top3               (partial-query (limit 3))
          by-population-desc (partial-query (sort { :population -1 }))
          _                  (mc/insert-batch db coll [ma-doc de-doc ny-doc ca-doc tx-doc])
          result             (with-collection db coll
                               (find {})
                               (merge top3)
                               (merge by-population-desc)
                               (keywordize-fields false))]
      ;; documents have fields as strings,
      ;; not keywords
      (is (= (map #(% "name") result)
             (map #(% :name) [ca-doc tx-doc ny-doc]))))))
