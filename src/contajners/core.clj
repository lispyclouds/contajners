(ns contajners.core
  (:require
    [unixsocket-http.core :as http]
    [contajners.impl :as impl]))

(defn categories
  [engine version]
  (->> (impl/load-api engine version)
       (keys)
       (impl/remove-internal-meta)))

(defn client
  [{:keys [engine category conn version]}]
  (let [api                    (impl/load-api engine version)
        {:keys [uri
                connect-timeout
                read-timeout
                write-timeout
                call-timeout
                mtls]}         conn]
    {:api     (-> api
                  category
                  (merge (select-keys api [:contajners/doc-url])))
     :conn    (http/client uri
                           {:connect-timeout-ms connect-timeout
                            :read-timeout-ms    read-timeout
                            :write-timeout-ms   write-timeout
                            :call-timeout-ms    call-timeout
                            :mode               :recreate
                            :builder-fn         (if mtls
                                                  (impl/make-builder-fn mtls)
                                                  identity)})
     :version version}))

(defn ops
  [{:keys [api]}]
  (->> api
       (keys)
       (impl/remove-internal-meta)))

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
  [{:keys [version conn api]} {:keys [op params data as throw-exceptions throw-entire-message]}]
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
                        :body                  data
                        :as                    (or as :string)
                        :throw-exceptions      throw-exceptions
                        :throw-entire-message? throw-entire-message}
        response       (-> request
                           (impl/maybe-serialize-body)
                           (http/request)
                           (:body))]
    (case as
      (:socket :stream) response
      (impl/try-json-parse response))))

(comment
  (categories :podman "v3.2.3")

  (categories :docker "v1.41")

  (def client
    (client {:engine   :podman
             :version  "v3.2.3"
             :category :libpod/images
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
           :params {:all true}})

  (def d-images
    (client {:engine   :docker
             :version  "v1.41"
             :category :images
             :conn     {:uri "unix:///var/run/docker.sock"}}))

  (ops d-images)

  (doc d-images :ImageCreate)

  (invoke d-images
          {:op     :ImageCreate
           :params {:fromImage "busybox:musl"}})

  (invoke d-client
          {:op                   :ContainerCreate
           :params               {:name "conny"}
           :data                 {:Image "busybox:musl"}
           :throw-exceptions     true
           :throw-entire-message true}))
