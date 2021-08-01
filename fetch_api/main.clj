(ns main
  (:require
    [clojure.java.io :as io]
    [clojure.string :as s]
    [java-http-clj.core :as http])
  (:import
    [io.swagger.parser OpenAPIParser]
    [io.swagger.v3.oas.models Operation PathItem]
    [io.swagger.v3.oas.models.parameters Parameter]
    [io.swagger.v3.parser.core.models ParseOptions]))

(def sources
  {:docker {:url      "https://docs.docker.com/engine/api/%s.yaml"
            :doc-url  "https://docs.podman.io/en/%s/_static/api.html#operation/%s"
            :versions ["v1.25"
                       "v1.26"
                       "v1.27"
                       "v1.28"
                       "v1.29"
                       "v1.30"
                       "v1.31"
                       "v1.32"
                       "v1.33"
                       "v1.34"
                       "v1.35"
                       "v1.36"
                       "v1.37"
                       "v1.38"
                       "v1.39"
                       "v1.40"
                       "v1.41"]}
   :podman {:url        "https://storage.googleapis.com/libpod-master-releases/swagger-%s.yaml"
            :doc-url    "https://docs.docker.com/engine/api/%s/#operation/%s"
            :namespaces #{"/libpod"}
            :versions   ["v3.1.0"
                         "v3.1.1"
                         "v3.1.2"
                         "v3.2.0"
                         "v3.2.1"
                         "v3.2.2"
                         "v3.2.3"]}})

(defn find-first
  [pred coll]
  (some #(when (pred %) %) coll))

;; TODO: Better?
(defn ->category
  "Given a path and a set of namespaces, returns the (namespaced)category.

  path: /containers/json
  namespaces: #{}
  category: :containers

  path: /libpod/containers/json
  namespaces: #{\"/libpod\"}
  category: :libpod/containers

  path: /libpod/deeper/api/containers/json
  namespaces: #{\"/libpod/deeper/api\"}
  category: :libpod.deeper.api/containers

  The category is the prefix of the path being passed. eg /containers, /images
  The set of namespaces, if passed, determines if the category is to be namespaced. eg /libpod/containers and /containers
  The namespace is useful to distinguish similarly named ops in APIs supporting compatibility with other engines."
  [path namespaces]
  (let [matched  (find-first #(s/starts-with? path %) namespaces)
        nspace   (when matched
                   (-> matched
                       (subs 1)
                       (s/replace "/" ".")))
        path     (if matched
                   (s/replace-first path matched "")
                   path)
        category (-> path
                     (subs 1)
                     (s/split #"/")
                     (first))]
    (if nspace
      (keyword nspace category)
      (keyword category))))

;; TODO: Parse and validate the types someday
(defn ->params
  "Given a io.swagger.v3.oas.models.parameters.Parameter, returns a map of necessary keys."
  [^Parameter param]
  {:name (.getName param)
   :in   (keyword (.getIn param))})

(defn ->operation
  "Given a path, http method and an io.swagger.v3.oas.models.Operation, returns a map of operation id and necessary keys."
  [path method ^Operation operation]
  (let [op {:summary (.getSummary operation)
            :method  (-> method
                         str
                         s/lower-case
                         keyword)
            :path    path
            :params  (map ->params (.getParameters operation))}
        request-body (.getRequestBody operation)]
    {(keyword (.getOperationId operation)) (if request-body
                                             (assoc op :request-body true)
                                             op)}))

(defn ->operations
  "Given a set of namespaces, path and a io.swagger.v3.oas.models.PathItem returns a list of maps of operations."
  [namespaces path ^PathItem path-item]
  (->> (.readOperationsMap path-item)
       (map #(->operation path (key %) (val %)))
       (map #(hash-map (->category path namespaces) %))))

(defn parse
  "Given a set of namespaces and the OpenAPI 2.0 spec as a string, returns the spec in the following format:
  {:category1 {:operation-id1 {:summary \"summary\"
                               :method  :HTTP_METHOD
                               :path    \"/path/to/api\"
                               :params  [{:name \"..\"}]}}
   :namespaced/category {...}}"
  [^String spec namespaces]
  (let [parse-options (doto (ParseOptions.)
                        (.setResolveFully true))]
    (->> (.readContents (OpenAPIParser.) spec nil parse-options) ; specs are still Swagger 2.0 🙄
         (.getOpenAPI)
         (.getPaths)
         (mapcat #(->operations namespaces (key %) (val %)))
         (apply (partial merge-with into)))))

(defn write-api
  "Writes the data to a path, creating it if non-existent."
  [content path]
  (when-not (.exists (io/file path))
    (io/make-parents path))
  (with-open [w (io/writer path)]
    (binding [*print-length* false
              *out* w]
      (pr content))))

;; TODO: Download in async
(defn dowload-and-process
  "Given the engine, url-template, api version and namespaces, downloads and writes out as an edn file in resources."
  [engine url-template doc-url version namespaces]
  (println (format "Processing version %s for %s"
                   version
                   engine))
  (let [{:keys [status body]} (http/get (format url-template version))]
    (if (>= status 400)
      (throw (RuntimeException. (format "Error fetching version %s: %s"
                                        version
                                        body)))
      (-> body
          (parse namespaces)
          (assoc :contajners/doc-url doc-url)
          (write-api (format "resources/contajners/%s/%s.edn"
                             (name engine)
                             version))))))

(defn run
  "Driver fn, iterates over the sources, downloads, processes and saves as resources."
  [& _]
  (let [download-info (for [[engine {:keys [url doc-url namespaces versions]}] sources
                            version versions]
                        [engine url doc-url version namespaces])]
    (->> download-info
         (pmap #(apply dowload-and-process %))
         (dorun))))

(comment
  (set! *warn-on-reflection* true)

  (run)

  (find-first #{3} [1 2 3 4 5])

  (->category "/libpod/containers" #{"/libpod"})

  (->category "/containers/json" #{})

  (write-api "resources/contajners/docker/blah.edn" {:a 42})

  (dowload-and-process :docker
                       "https://docs.docker.com/engine/api/v%s.yaml"
                       "https://docs.podman.io/en/%s/_static/api.html#operation/%s"
                       "v1.41"
                       #{})

  (dowload-and-process :podman
                       "https://storage.googleapis.com/libpod-master-releases/swagger-v3.2.2.yaml"
                       "https://docs.docker.com/engine/api/%s/#operation/%s"
                       "v3.2.2"
                       #{"/libpod"})

  (-> "https://storage.googleapis.com/libpod-master-releases/swagger-v3.2.2.yaml"
      http/get
      :body
      (parse #{"/libpod"}))

  (-> "https://docs.docker.com/engine/api/v1.41.yaml"
      http/get
      :body
      (parse #{})))
