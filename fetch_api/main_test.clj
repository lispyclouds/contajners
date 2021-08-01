(ns main-test
  (:require
    [clojure.test :as t]
    [main :as m])
  (:import
    [io.swagger.v3.oas.models Operation PathItem]
    [io.swagger.v3.oas.models.media StringSchema]
    [io.swagger.v3.oas.models.parameters PathParameter QueryParameter]))

(t/deftest test-find-first
  (t/testing "finds the first matching pred"
    (t/is (= 3
             (m/find-first #{3} [1 2 3 4 5]))))
  (t/testing "returns nil when not found"
    (t/is (nil? (m/find-first #{42} [1 2 3 4 5])))))

(t/deftest test->category
  (t/testing "categorize operation without namespace"
    (t/is (= :containers
             (m/->category "/containers/json" #{})))
    (t/is (= :containers
             (m/->category "/containers/json" #{"nope"}))))
  (t/testing "categorize operation with namespace"
    (t/is (= :libpod/containers
             (m/->category "/libpod/containers/json" #{"/libpod"}))))
  (t/testing "categorize operation with a nested namespace"
    (t/is (= :libpod.deeper.api/containers
             (m/->category "/libpod/deeper/api/containers/json" #{"/libpod/deeper/api"})))))

(t/deftest test->params
  (t/testing "param parsing"
    (let [param (doto (QueryParameter.)
                  (.setName "id")
                  (.setSchema (StringSchema.)))]
      (t/is (= {:name     "id"
                :in       :query
                :required false
                :schema   "string"}
               (m/->params param))))))

(t/deftest test->operation
  (t/testing "creating an operation map"
    (let [param     (doto (PathParameter.)
                      (.setName "id")
                      (.setSchema (StringSchema.)))
          operation (doto (Operation.)
                      (.setSummary "this is a test op")
                      (.setParameters [param])
                      (.setOperationId "TestOp"))]
      (t/is (= {:TestOp {:summary "this is a test op"
                         :method  :get
                         :path    "/test/path"
                         :params  [{:name     "id"
                                    :in       :path
                                    :required true
                                    :schema   "string"}]}}
               (m/->operation "/test/path" "GET" operation))))))

(t/deftest test->operations
  (t/testing "creating operations"
    (let [param     (doto (PathParameter.)
                      (.setName "id")
                      (.setSchema (StringSchema.)))
          operation (doto (Operation.)
                      (.setSummary "this is a test op")
                      (.setParameters [param])
                      (.setOperationId "TestOp"))
          path-item (doto (PathItem.)
                      (.setGet operation))]
      (t/is (= [{:containers {:TestOp {:summary "this is a test op"
                                       :method  :get
                                       :path    "/containers/json"
                                       :params  [{:name     "id"
                                                  :in       :path
                                                  :required true
                                                  :schema   "string"}]}}}]
               (m/->operations nil "/containers/json" path-item))))))
