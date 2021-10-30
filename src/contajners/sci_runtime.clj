(ns contajners.sci-runtime
  (:require
   [babashka.curl :as curl]))

(defn http-client [uri {:keys [connect-timeout
                              read-timeout
                              write-timeout
                              call-timeout]}]
  {:uri uri
   :connect-timeout connect-timeout
   :read-timeout read-timeout
   :write-timeout write-timeout
   :call-timeout call-timeout})

(defn http-request [var & more]
  (let [{:keys [url client]} var
        {:keys [uri]} client
        url (str "http://localhost" url)]
    (println (:body (curl/request {:debug true
                                   :url url
                                   :raw-args ["--unix-socket" uri]})))))

(defn http-get [client url]
  (let [{:keys [conn]} client
        {:keys [uri]} conn
        url (str "http://localhost" url)]
    (:body (curl/get url {:url url
                          :raw-args ["--unix-socket" uri]}))))
