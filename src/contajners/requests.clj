(ns contajners.requests
  (:require [unixsocket-http.core :as http]))

(comment
  (def client (http/client "tcp://localhost:8080"))

  (http/get client "/v1.40/_ping")

  (http/get client "/containers/json"))
