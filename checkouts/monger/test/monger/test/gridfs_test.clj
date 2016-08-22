(ns monger.test.gridfs-test
  (:refer-clojure :exclude [count remove find])
  (:require [monger.gridfs :as gridfs]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [monger.core :as mg :refer [count]]
            [monger.operators :refer :all]
            [monger.conversion :refer :all]
            [monger.gridfs :refer [store make-input-file store-file filename content-type metadata]])
  (:import [java.io InputStream File FileInputStream]
           [com.mongodb.gridfs GridFS GridFSInputFile GridFSDBFile]))

(let [conn (mg/connect)
      db   (mg/get-db conn "monger-test")
      fs   (mg/get-gridfs conn "monger-test")]
  (defn purge-gridfs*
    []
    (gridfs/remove-all fs))

  (defn purge-gridfs
    [f]
    (gridfs/remove-all fs)
    (f)
    (gridfs/remove-all fs))

  (use-fixtures :each purge-gridfs)

  (deftest ^{:gridfs true} test-storing-files-to-gridfs-using-relative-fs-paths
    (let [input "./test/resources/mongo/js/mapfun1.js"]
      (is (= 0 (count (gridfs/all-files fs))))
      (store (make-input-file fs input)
             (.setFilename "monger.test.gridfs.file1")
             (.setContentType "application/octet-stream"))
      (is (= 1 (count (gridfs/all-files fs))))))


  (deftest ^{:gridfs true} test-storing-files-to-gridfs-using-file-instances
    (let [input (io/as-file "./test/resources/mongo/js/mapfun1.js")]
      (is (= 0 (count (gridfs/all-files fs))))
      (store-file (make-input-file fs input)
                  (filename "monger.test.gridfs.file2")
                  (content-type "application/octet-stream"))
      (is (= 1 (count (gridfs/all-files fs))))))

  (deftest ^{:gridfs true} test-storing-bytes-to-gridfs
    (let [input (.getBytes "A string")
          md    {:format "raw" :source "AwesomeCamera D95"}
          fname  "monger.test.gridfs.file3"
          ct     "application/octet-stream"]
      (is (= 0 (count (gridfs/all-files fs))))
      (store-file (make-input-file fs input)
                  (filename fname)
                  (metadata md)
                  (content-type "application/octet-stream"))
      (let [f (first (gridfs/files-as-maps fs))]
        (is (= ct (:contentType f)))
        (is (= fname (:filename f)))
        (is (= md (:metadata f))))
      (is (= 1 (count (gridfs/all-files fs))))))

  (deftest ^{:gridfs true} test-storing-files-to-gridfs-using-absolute-fs-paths
    (let [tmp-file (File/createTempFile "monger.test.gridfs" "test-storing-files-to-gridfs-using-absolute-fs-paths")
          _        (spit tmp-file "Some content")
          input    (.getAbsolutePath tmp-file)]
      (is (= 0 (count (gridfs/all-files fs))))
      (store-file (make-input-file fs input)
                  (filename "monger.test.gridfs.file4")
                  (content-type "application/octet-stream"))
      (is (= 1 (count (gridfs/all-files fs))))))

  (deftest ^{:gridfs true} test-storing-files-to-gridfs-using-input-stream
    (let [tmp-file (File/createTempFile "monger.test.gridfs" "test-storing-files-to-gridfs-using-input-stream")
          _        (spit tmp-file "Some other content")]
      (is (= 0 (count (gridfs/all-files fs))))
      (store-file fs
                  (make-input-file (FileInputStream. tmp-file))
                  (filename "monger.test.gridfs.file4b")
                  (content-type "application/octet-stream"))
      (is (= 1 (count (gridfs/all-files fs))))))



  (deftest ^{:gridfs true} test-finding-individual-files-on-gridfs
    (testing "gridfs/find-one"
      (purge-gridfs*)
      (let [input   "./test/resources/mongo/js/mapfun1.js"
            ct     "binary/octet-stream"
            fname  "monger.test.gridfs.file5"
            md5    "14a09deabb50925a3381315149017bbd"
            stored (store-file (make-input-file fs input)
                               (filename fname)
                               (content-type ct))]
        (is (= 1 (count (gridfs/all-files fs))))
        (is (:_id stored))
        (is (:uploadDate stored))
        (is (= 62 (:length stored)))
        (is (= md5 (:md5 stored)))
        (is (= fname (:filename stored)))
        (is (= ct (:contentType stored)))
        (are [a b] (is (= a (:md5 (from-db-object (gridfs/find-one fs b) true))))
             md5 {:_id (:_id stored)}
             md5 (to-db-object {:md5 md5}))))
    (testing "gridfs/find-one-as-map"
      (purge-gridfs*)
      (let [input   "./test/resources/mongo/js/mapfun1.js"
            ct      "binary/octet-stream"
            fname "monger.test.gridfs.file6"
            md5      "14a09deabb50925a3381315149017bbd"
            stored  (store-file (make-input-file fs input)
                                (filename fname)
                                (metadata (to-db-object {:meta "data"}))
                                (content-type ct))]
        (is (= 1 (count (gridfs/all-files fs))))
        (is (:_id stored))
        (is (:uploadDate stored))
        (is (= 62 (:length stored)))
        (is (= md5 (:md5 stored)))
        (is (= fname (:filename stored)))
        (is (= ct (:contentType stored)))
        (let [m (gridfs/find-one-as-map fs {:filename fname})]
          (is (= {:meta "data"} (:metadata m))))
        (are [a query] (is (= a (:md5 (gridfs/find-one-as-map fs query))))
             md5 {:_id (:_id stored)}
             md5 {:md5 md5})))
    (testing "gridfs/find-by-id"
      (purge-gridfs*)
      (let [input   "./test/resources/mongo/js/mapfun1.js"
            ct     "binary/octet-stream"
            fname  "monger.test.gridfs.file5"
            md5    "14a09deabb50925a3381315149017bbd"
            stored (store-file (make-input-file fs input)
                               (filename fname)
                               (content-type ct))]
        (is (= 1 (count (gridfs/all-files fs))))
        (is (:_id stored))
        (is (:uploadDate stored))
        (is (= 62 (:length stored)))
        (is (= md5 (:md5 stored)))
        (is (= fname (:filename stored)))
        (is (= ct (:contentType stored)))
        (are [a id] (is (= a (:md5 (from-db-object (gridfs/find-by-id fs id) true))))
             md5 (:_id stored))))
    (testing "gridfs/find-map-by-id"
      (purge-gridfs*)
      (let [input   "./test/resources/mongo/js/mapfun1.js"
            ct      "binary/octet-stream"
            fname "monger.test.gridfs.file6"
            md5      "14a09deabb50925a3381315149017bbd"
            stored  (store-file (make-input-file fs input)
                                (filename fname)
                                (metadata (to-db-object {:meta "data"}))
                                (content-type ct))]
        (is (= 1 (count (gridfs/all-files fs))))
        (is (:_id stored))
        (is (:uploadDate stored))
        (is (= 62 (:length stored)))
        (is (= md5 (:md5 stored)))
        (is (= fname (:filename stored)))
        (is (= ct (:contentType stored)))
        (let [m (gridfs/find-map-by-id fs (:_id stored))]
          (is (= {:meta "data"} (:metadata m))))
        (are [a id] (is (= a (:md5 (gridfs/find-map-by-id fs id))))
          md5 (:_id stored)))))

  (deftest ^{:gridfs true} test-finding-multiple-files-on-gridfs
    (let [input   "./test/resources/mongo/js/mapfun1.js"
          ct      "binary/octet-stream"
          md5      "14a09deabb50925a3381315149017bbd"
          stored1  (store-file (make-input-file fs input)
                               (filename "monger.test.gridfs.file6")
                               (content-type ct))
          stored2  (store-file (make-input-file fs input)
                               (filename "monger.test.gridfs.file7")
                               (content-type ct))
          list1    (gridfs/find-by-filename fs "monger.test.gridfs.file6")
          list2    (gridfs/find-by-filename fs "monger.test.gridfs.file7")
          list3    (gridfs/find-by-filename fs "888000___.monger.test.gridfs.file")
          list4    (gridfs/find-by-md5 fs md5)]
      (is (= 2 (count (gridfs/all-files fs))))
      (are [a b] (is (= (map #(.get ^GridFSDBFile % "_id") a)
                        (map :_id b)))
           list1 [stored1]
           list2 [stored2]
           list3 []
           list4 [stored1 stored2])))


  (deftest ^{:gridfs true} test-removing-multiple-files-from-gridfs
    (let [input   "./test/resources/mongo/js/mapfun1.js"
          ct      "binary/octet-stream"
          md5      "14a09deabb50925a3381315149017bbd"
          stored1  (store-file (make-input-file fs input)
                               (filename "monger.test.gridfs.file8")
                               (content-type ct))
          stored2  (store-file (make-input-file fs input)
                               (filename "monger.test.gridfs.file9")
                               (content-type ct))]
      (is (= 2 (count (gridfs/all-files fs))))
      (gridfs/remove fs { :filename "monger.test.gridfs.file8" })
      (is (= 1 (count (gridfs/all-files fs))))
      (gridfs/remove fs { :md5 md5 })
      (is (= 0 (count (gridfs/all-files fs)))))))
