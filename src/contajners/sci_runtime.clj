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
  (throw (RuntimeException. "Curl request for bb not implemented yet")))

(defn http-get [client url]
  (throw (RuntimeException. "Curl http get for bb not implemented yet")))
