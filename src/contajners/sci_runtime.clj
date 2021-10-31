(ns contajners.sci-runtime
  (:require
   [babashka.curl :as curl]
   [clojure.string :as string])
  (:import
   [java.net URI]))

(defn http-client [uri {:keys [connect-timeout
                               read-timeout
                               write-timeout
                               call-timeout
                               mtls]}]
  {:uri uri
   :connect-timeout connect-timeout
   :read-timeout read-timeout
   :write-timeout write-timeout
   :call-timeout call-timeout
   :mtls mtls})

(defn- unix-socket?
  [^String uri]
  (= "unix" (.getScheme (URI. uri))))

(defn- may-be-unix-socket [{{:keys [uri]} :client}]
  (if (unix-socket? uri)
    {:raw-args ["--unix-socket" (.getPath (URI. uri))]
     :uri "http://localhost"}
    {:uri uri}))

(defn- add-url [url {:keys [uri] :as req}]
  (->> (str uri url)
       (assoc req :url)
       (#(dissoc % :uri))))

(defn request [req]
  (let [{:keys [client method url headers query-params body]} req
        {:keys [uri connect-timeout read-timeout write-timeout call-timeout mtls debug]} client]
    (->> req
         may-be-unix-socket
         (add-url url)
         (merge {:debug debug
                 :method method
                 :query-params query-params
                 :headers headers
                 :body body})
         curl/request
         ((fn [o] (do (println o) (println req) o))) ;;TODO: for debugging purpose, remove later
         )))

(comment
  (request
   {:method :get,
    :as :string,
    :client {:uri "unix:///var/run/docker.sock",
             :connect-timeout nil,
             :read-timeout nil,
             :write-timeout nil,
             :call-timeout nil},
    :headers nil,
    :throw-entire-message? nil,
    :url "/v1.41/containers/json",
    :query-params {:all true},
    :throw-exceptions nil,
    :body nil}
   )
)
