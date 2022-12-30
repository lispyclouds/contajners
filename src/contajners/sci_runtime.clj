(ns contajners.sci-runtime
  (:require
    [babashka.curl :as curl])
  (:import
    [java.net URI]))

(defn- unix-socket?
  [^String uri]
  (= "unix" (.getScheme (URI. uri))))

(defn add-curl-opts
  [uri connect-timeout call-timeout mtls]
  (let [unix     (unix-socket? uri)
        raw-args (if connect-timeout
                   ["--connect-timeout"
                    (-> (/ connect-timeout 1000)
                        int
                        str)]
                   [])
        raw-args (if call-timeout
                   (conj raw-args
                         "--max-timeout"
                         (-> (/ call-timeout 1000)
                             int
                             str))
                   raw-args)
        raw-args (if unix
                   (conj raw-args "--unix-socket" (.getPath (URI. uri)))
                   raw-args)
        raw-args (if mtls
                   (conj raw-args "--cacert" (:ca mtls) "--key" (:key mtls) "--cert" (:cert mtls))
                   raw-args)]
    {:raw-args raw-args
     :url      (if unix
                 "http://localhost"
                 uri)}))

(defn client
  [uri {:keys [connect-timeout-ms call-timeout-ms mtls]}]
  (add-curl-opts uri connect-timeout-ms call-timeout-ms mtls))

(defn adapt-multi-params
  "Adapts the params to support keys having multiple values for bb curl.
  Generally the case for headers or query params.

  {:a [1 2 3] :b 42} => [[:a 1] [:a 2] [:a 3] [:b 42]]
  {:a 41 :b 42} => [[:a 41] [:b 42]]"
  [query-params]
  (->> query-params
       (mapcat (fn [[k v]]
                 (if (sequential? v)
                   (map #(vector k
                                 %)
                        v)
                   [[k v]])))
       (into [])))

(defn request
  "Internal fn to perform the request."
  [{:keys [client method path headers query-params body as throw-exceptions]}]
  (when (= :socket as)
    (throw (IllegalArgumentException. ":as :socket is currently unsupported on this runtime, use :stream or :data")))
  (-> {:method       method
       :query-params (adapt-multi-params query-params)
       :headers      (adapt-multi-params headers)
       :body         body
       :as           (if (= :data as)
                       nil
                       as)
       :throw        throw-exceptions}
      (into (update-in client [:url] str path))
      (curl/request)
      (:body)))

(comment
  (adapt-multi-params {:a [1 2 3] :b 42})

  (adapt-multi-params {:a 41 :b 42})

  (add-curl-opts "unix:///var/run/docker.sock" 1000 2000 "/v1.41/containers/json")

  (request
    {:method       :get
     :client       (client "unix:///var/run/docker.sock" {})
     :path         "/v1.41/containers/json"
     :query-params {:all true}}))
