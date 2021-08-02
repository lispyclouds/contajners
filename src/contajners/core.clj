(ns contajners.core
  (:require
    [clojure.data.json :as json]
    [unixsocket-http.core :as http]
    [contajners.impl :as impl]))

(defn categories
  [engine version]
  (->> (impl/load-api engine version)
       (keys)
       (remove #(= "contajners" (namespace %)))))

(defn client
  [{:keys [engine category conn version]}]
  (let [api (impl/load-api engine version)]
    {:api     (-> api
                  category
                  (merge (select-keys api [:contajners/doc-url])))
     :conn    (http/client (:uri conn) {:mode :recreate})
     :version version}))

(defn ops
  [{:keys [api]}]
  (keys api))

(defn doc
  [{:keys [version api]} op]
  (some-> api
          op
          (select-keys [:summary])
          (assoc :doc-url
                 (format (:contajners/doc-url api)
                         version
                         (name op)))))

(defn invoke
  [{:keys [version conn api]} {:keys [op params as throw-exceptions throw-entire-message]}]
  (let [operation      (op api)
        request-params (reduce (partial impl/gather-params params)
                               {}
                               (:params operation))
        request        {:client                conn
                        :method                (:method operation)
                        :url                   (-> operation
                                                   :path
                                                   (impl/interpolate-path (:path request-params))
                                                   (as-> path (str "/" version path)))
                        :headers               (:headers request-params)
                        :query-params          (:query request-params)
                        :body                  (:body params)
                        :as                    (or as :string)
                        :throw-exceptions      throw-exceptions
                        :throw-entire-message? throw-entire-message}]
    (-> request
        (impl/maybe-serialize-body)
        (http/request)
        :body
        (json/read-str :key-fn keyword))))

(comment
  (categories :podman "v3.2.3")

  (categories :docker "v1.41")

  (def client
    (client {:engine   :podman
             :version  "v3.2.3"
             :category :libpod/containers
             :conn     {:uri "http://localhost:8080"}}))

  (def d-client
    (client {:engine   :docker
             :version  "v1.41"
             :category :containers
             :conn     {:uri "unix:///var/run/docker.sock"}}))

  (ops client)

  (ops d-client)

  (doc client :ContainerCreateLibpod)

  (doc d-client :ContainerCreate)

  (invoke client
          {:op     :ContainerListLibpod
           :params {:all true}})

  (invoke d-client
          {:op     :ContainerList
           :params {:all true}}))
