(ns contajners.sci-runtime
  (:require
   [babashka.curl :as curl])
  (:import
   [java.net URI]))

(defn- unix-socket?
  [^String uri]
  (= "unix" (.getScheme (URI. uri))))

(defn as-sec-str
  [ms]
  (-> (/ ms 1000) int str))

(defn client
  [uri {:keys [connect-timeout-ms call-timeout-ms mtls]}]
  (let [unix (unix-socket? uri)]
    {:raw-args (cond-> []
                 connect-timeout-ms (conj "--connect-timeout" (as-sec-str connect-timeout-ms))
                 call-timeout-ms (conj "--max-time" (as-sec-str call-timeout-ms))
                 unix (conj "--unix-socket" (.getPath (URI. uri)))
                 mtls (conj "--cacert" (:ca mtls) "--key" (:key mtls) "--cert" (:cert mtls)))
     :url (if unix "http://localhost" uri)}))

(defn adapt-multi-params
  "Adapts the params to support keys having multiple values for bb curl.
  Generally the case for headers or query params.

  {:a [1 2 3] :b 42} => [[:a 1] [:a 2] [:a 3] [:b 42]]
  {:a 41 :b 42} => [[:a 41] [:b 42]]"
  [query-params]
  (->> query-params
       (mapcat (fn [[k v]]
                 (if (sequential? v)
                   (map #(vector k %) v)
                   [[k v]])))
       (into [])))

(defn request
  "Internal fn to perform the request."
  [{:keys [client method path headers query-params body as throw-exceptions]}]
  (when (= :socket as)
    (throw (IllegalArgumentException. ":as :socket is currently unsupported on this runtime, use :stream or :data")))
  (-> {:method method
       :query-params (adapt-multi-params query-params)
       :headers (adapt-multi-params headers)
       :body body
       :as (if (= :data as) nil as)
       :throw throw-exceptions}
      (into (update-in client [:url] str path))
      (curl/request)
      (:body)))

(comment
  (adapt-multi-params {:a [1 2 3] :b 42})

  (adapt-multi-params {:a 41 :b 42})

  (request
   {:method :get
    :client (client "unix:///var/run/docker.sock" {})
    :path "/v1.41/containers/json"
    :query-params {:all true}}))
