(ns contajners.core
  (:require
    #?(:bb [contajners.sci-runtime :as rt]
       :clj [contajners.jvm-runtime :as rt])
    [contajners.impl :as impl]))

(defn categories
  "Returns the available categories for an engine at a specified verison.

  Categories are the kind of operations the engine can do.

  eg. :docker and v1.41
      :podman and v3.2.3"
  [engine version]
  (->> (impl/load-api engine version)
       (keys)
       (impl/remove-internal-meta)))

(defn client
  "Creates a client scoped to an engine, category, connection settings and API version.

  Connection settings:
  uri: The full URI with the protocol for the connection to the engine.
  read-timeout: Read timeout in ms.
  write-timeout: Write timeout in ms.
  call-timeout: Total round trip timeout in ms.
  mtls: A map having the paths to the CA, key and cert to perform Mutual TLS with the engine."
  [{:keys [engine category conn version]}]
  (let [api (impl/load-api engine version)
        {:keys [uri
                connect-timeout
                read-timeout
                write-timeout
                call-timeout
                mtls]}
        conn]
    {:category category
     :api      (-> api
                   category
                   (merge (select-keys api [:contajners/doc-url])))
     :conn     (rt/http-client uri
                               {:connect-timeout-ms connect-timeout
                                :read-timeout-ms    read-timeout
                                :write-timeout-ms   write-timeout
                                :call-timeout-ms    call-timeout
                                :mode               :recreate
                                :mtls               mtls})
     :version  version}))

(defn ops
  "Returns the supported operations for a client."
  [{:keys [api]}]
  (->> api
       (keys)
       (impl/remove-internal-meta)))

(defn doc
  "Returns the summary and doc URL of the operation in the client."
  [{:keys [version api]} op]
  (some-> api
          op
          (select-keys [:summary])
          (assoc :doc-url
                 (format (:contajners/doc-url api)
                         version
                         (name op)))))

(defn invoke
  "Performs the operation with the specified client and a map of options.

  Options map:
  op: The operation to invoke on the engine. Required.
  params: The params needed for the operation. Default: {}.
  data: The payload needed to be sent to the op. Maps will be JSON serialized. Corresponds to the Request Body in docs. Default: {}.
  as: The return type of the response. :data, :stream, :socket. Default: :data.
  throw-exceptions: Throws exceptions when status is >= 400 for API calls. Default: false.
  throw-entire-message: Includes the full exception as a string. Default: false."
  [{:keys [version conn api category]} {:keys [op params data as throw-exceptions throw-entire-message]}]
  (if-let [operation (op api)]
    (let [request-params (reduce (partial impl/gather-params params)
                                 {}
                                 (:params operation))
          request        {:client               conn
                          :method               (:method operation)
                          :path                 (-> operation
                                                    :path
                                                    (impl/interpolate-path (:path request-params))
                                                    (as-> path (str "/" version path)))
                          :headers              (:headers request-params)
                          :query-params         (:query request-params)
                          :body                 data
                          :as                   as
                          :throw-exceptions     throw-exceptions
                          :throw-entire-message throw-entire-message}
          response       (-> request
                             (impl/maybe-serialize-body)
                             (rt/request))]
      (case as
        (:socket :stream) response
        (impl/try-json-parse response)))
    (impl/bail-out (format "Invalid operation %s for category %s"
                           (name op)
                           (name category)))))

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
          {:op     :ImageListLibpod
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
