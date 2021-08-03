(ns contajners.impl
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.data.json :as json]
    [clojure.string :as s]
    [unixsocket-http.core :as http])
  (:import
    [java.io PushbackReader]
    [java.util.regex Pattern]))

(defn remove-internal-meta
  [data-seq]
  (remove #(= "contajners" (namespace %)) data-seq))

(defn load-api
  [engine version]
  (if-let [config (io/resource
                    (format "contajners/%s/%s.edn"
                            (name engine)
                            version))]
    (-> config
        (io/reader)
        (PushbackReader.)
        (edn/read))
    (throw (IllegalArgumentException. "Cannot load api, the engine, version combo may not be supported."))))

(defn gather-params
  [supplied-params request-params {:keys [name in]}]
  (let [param (keyword name)]
    (if-not (contains? supplied-params param)
      request-params
      (update-in request-params [(keyword in)] assoc param (param supplied-params)))))

(defn maybe-serialize-body
  "If the body is a map, convert it to JSON and attach the correct headers."
  [{:keys [body] :as request}]
  (if (map? body)
    (-> request
        (assoc-in [:headers "content-type"] "application/json")
        (update :body json/write-str))
    request))

(defn interpolate-path
  "Replaces all occurrences of {k1}, {k2} ... with the value map provided.
  Example:
  given a/path/{id}/on/{not-this}/root/{id} and {:id hello}
  results in: a/path/hello/{not-this}/root/hello."
  [path value-map]
  (let [[param value] (first value-map)]
    (if (nil? param)
      path
      (recur (s/replace path
                        (re-pattern (format "\\{([%s].*?)\\}"
                                            (-> param
                                                name
                                                Pattern/quote)))
                        (str value))
             (dissoc value-map param)))))

(defn try-json-parse
  [value]
  (try
    (json/read-str value :key-fn keyword)
    (catch Exception _ value)))

(comment
  (remove-internal-meta [:contajners/foo :foo])

  (try-json-parse "yesnt")

  (try-json-parse "[1, 2, 3]")

  (load-api :podman "v3.2.3")

  (def client (http/client "tcp://localhost:8080"))

  (http/get client "/v1.40/_ping")

  (http/get client "/containers/json")

  (reduce (partial gather-params {:a 42 :b 64 :c 44})
          {}
          [{:name "a" :in :path}
           {:name "b" :in :query}
           {:name "c" :in :query}])

  (maybe-serialize-body {:body {:a 42}})

  (maybe-serialize-body {:body 42})

  (interpolate-path "/a/{w}/b/{x}/{y}" {:x 41 :y 42 :z 43}))
