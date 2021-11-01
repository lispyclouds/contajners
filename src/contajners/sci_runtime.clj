(ns contajners.sci-runtime
  (:require
    [babashka.curl :as curl])
  (:import
    [java.net URI]))

(defn client
  [uri {:keys [connect-timeout-ms call-timeout-ms mtls]}]
  {:uri             uri
   :connect-timeout connect-timeout-ms
   :call-timeout    call-timeout-ms
   :mtls            mtls})

(defn- unix-socket?
  [^String uri]
  (= "unix" (.getScheme (URI. uri))))

(defn add-curl-opts
  [{:keys [uri connect-timeout call-timeout mtls]} path]
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
                 (str "http://localhost" path)
                 (str uri path))}))

(defn request
  "Internal fn to perform the request."
  [{:keys [client method path headers query-params body as throw-exceptions]}]
  (when (= :socket as)
    (throw (IllegalArgumentException. ":as :socket is currently unsupported on this runtime, use: :stream or :data")))
  (-> {:method       method
       :query-params query-params
       :headers      headers
       :body         body
       :as           (if (= :data as)
                       nil
                       as)
       :throw        throw-exceptions}
      (into (add-curl-opts client path))
      (curl/request)
      (:body)))

(comment
  (add-curl-opts {:uri             "unix:///var/run/docker.sock"
                  :connect-timeout 1000
                  :call-timeout    2000}
                 "/v1.41/containers/json")

  (request
    {:method       :get
     :client       {:uri "unix:///var/run/docker.sock"}
     :path         "/v1.41/containers/json"
     :query-params {:all true}}))
