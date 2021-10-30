(ns contajners.sci-runtime
  (:require
   [babashka.curl :as curl]))

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

(defn http-request [req]
  (let [{:keys [client method url headers query-params body]} req
        {:keys [uri connect-timeout read-timeout write-timeout call-timeout mtls debug]} client
        url (str "http://localhost" url)]
    ((fn [o] (do (println o) (println req) o))
     (curl/request {:debug debug
                    :url url
                    :raw-args ["--unix-socket" "/var/run/docker.sock"]
                    :method method
                    :query-params query-params
                    :headers headers
                    :body body}))))

(defn http-get [client url]
  (let [{:keys [conn]} client
        {:keys [uri]} conn
        url (str "http://localhost" url)]
    (:body (curl/get url {:url url
                          :raw-args ["--unix-socket" uri]}))))
(comment
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
