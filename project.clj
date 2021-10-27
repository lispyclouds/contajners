(defproject org.clojars.lispyclouds/contajners "0.0.1"
  :author       "Rahul De <rahul@mailbox.org>"
  :url          "https://github.com/lispyclouds/contajners"
  :description  "An idiomatic, data-driven, REPL friendly clojure client for OCI container engines"
  :license      {:name "MIT"
                 :url  "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/data.json "2.4.0"]
                 [unixsocket-http/unixsocket-http "1.0.10"]
                 [com.squareup.okhttp3/okhttp-tls "4.9.2"]
                 [into-docker/pem-reader "1.0.2"]])
