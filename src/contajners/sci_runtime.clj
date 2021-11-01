(ns contajners.sci-runtime
  (:require
    [babashka.curl :as curl])
  (:import
    [java.net URI]))

(defn http-client
  [uri
   {:keys [connect-timeout
           read-timeout
           write-timeout
           call-timeout
           mtls]}]
  {:uri             uri
   :connect-timeout connect-timeout
   :read-timeout    read-timeout
   :write-timeout   write-timeout
   :call-timeout    call-timeout
   :mtls            mtls})

(defn- unix-socket?
  [^String uri]
  (= "unix" (.getScheme (URI. uri))))

(defn to-url
  [{:keys [uri]} path]
  (if (unix-socket? uri)
    {:raw-args ["--unix-socket" (.getPath (URI. uri))]
     :url      (str "http://localhost" path)}
    {:url (str uri path)}))

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
      (into (to-url client path))
      (curl/request)
      (:body)))

(comment
  (to-url {:uri             "unix:///var/run/docker.sock"
           :connect-timeout nil
           :read-timeout    nil
           :write-timeout   nil
           :call-timeout    nil}
          "/v1.41/containers/json")

  (request
    {:method       :get
     :client       {:uri "unix:///var/run/docker.sock"}
     :path          "/v1.41/containers/json"
     :query-params {:all true}}))
