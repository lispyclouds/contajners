(ns main
  (:require
   [babashka.http-client :as http]
   [clojure.java.io :as io]
   [clojure.string :as s])
  (:import
   [io.swagger.parser OpenAPIParser]
   [io.swagger.v3.oas.models Operation PathItem]
   [io.swagger.v3.oas.models.parameters Parameter]
   [io.swagger.v3.parser.core.models ParseOptions]
   [java.util.concurrent Executors]))

(def sources
  {:docker {:url "https://docs.docker.com/engine/api/%s.yaml"
            :doc-url "https://docs.docker.com/engine/api/%s/#operation/%s"
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
                       "v1.41"
                       "v1.42"]}
   :podman {:url "https://storage.googleapis.com/libpod-master-releases/swagger-%s.yaml"
            :doc-url "https://docs.podman.io/en/%s/_static/api.html#operation/%s"
            :namespaces #{"/libpod"}
            :versions ["v3.1.0"
                       "v3.1.1"
                       "v3.1.2"
                       "v3.2.0"
                       "v3.2.1"
                       "v3.2.2"
                       "v3.2.3"
                       "v3.3.0"
                       "v3.3.1"
                       "v3.4.0"
                       "v3.4.1"
                       "v3.4.2"
                       "v3.4.3"
                       "v3.4.4"
                       "v4.0.0"
                       "v4.0.1"
                       "v4.0.2"
                       "v4.0.3"
                       "v4.1.0"
                       "v4.1.1"
                       "v4.2.0"
                       "v4.2.1"
                       "v4.3.0"
                       "v4.3.1"
                       "v4.4.0"
                       "v4.4.1"
                       "v4.4.2"
                       "v4.4.3"
                       "v4.4.4"
                       "v4.5.0"
                       "v4.5.1"
                       "v4.6.0"]}})

(def resource-path "resources/contajners")

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
  (let [matched (find-first #(s/starts-with? path %) namespaces)
        nspace (when matched
                 (-> matched
                     (subs 1)
                     (s/replace "/" ".")))
        path (if matched
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
   :in (keyword (.getIn param))})

(defn ->operation
  "Given a path, http method and an io.swagger.v3.oas.models.Operation, returns a map of operation id and necessary keys."
  [path method ^Operation operation]
  (let [op {:summary (.getSummary operation)
            :method (-> method
                        str
                        s/lower-case
                        keyword)
            :path path
            :params (map ->params (.getParameters operation))}
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

(defn fetch-spec
  "Downloads the spec from the URL and version provided."
  [url-template version]
  (let [{:keys [status body]} (http/get (format url-template version)
                                        {:throw false})]
    (if (>= status 400)
      (binding [*out* *err*]
        (println (format "Error fetching version %s: %s" version body)))
      body)))

(defn process-spec
  "Processes the spec with the namespaces and adds metadata."
  [spec doc-url namespaces]
  (-> spec
      (parse namespaces)
      (assoc :contajners/doc-url doc-url)))

(defn write-spec
  "Writes the spec as minified edn."
  [spec engine version]
  (let [path (format "%s/%s/%s.edn"
                     resource-path
                     (name engine)
                     version)]
    (with-open [w (io/writer path)]
      (binding [*print-length* false
                *out* w]
        (pr spec)))))

(defn ensure-engine-dirs
  [engine]
  (let [path (io/file (str resource-path "/" (name engine)))]
    (when-not (.exists path)
      (.mkdirs path))))

(defn run
  "Driver fn, iterates over the sources, downloads, processes and saves as resources."
  [& _]
  (let [executor (Executors/newVirtualThreadPerTaskExecutor)
        download-info (for [[engine {:keys [url doc-url namespaces versions]}] sources
                            version versions]
                        {:engine engine
                         :url url
                         :doc-url doc-url
                         :version version
                         :namespaces namespaces})
        fetchers (map #(fn [] (fetch-spec (% :url) (% :version)))
                      download-info)
        fetched (->> (.invokeAll executor fetchers)
                     (map deref)
                     (filter some?))
        processed (pmap #(process-spec %1 (%2 :doc-url) (%2 :namespaces))
                        fetched
                        download-info)]
    (run! ensure-engine-dirs (keys sources))
    (->> (map #(fn [] (write-spec %1 (%2 :engine) (%2 :version)))
              processed
              download-info)
         (.invokeAll executor)
         (run! deref))))

(comment
  (set! *warn-on-reflection* true)

  (run)

  (find-first #{3} [1 2 3 4 5])

  (->category "/libpod/containers" #{"/libpod"})

  (->category "/containers/json" #{})

  (-> "https://storage.googleapis.com/libpod-master-releases/swagger-v3.2.2.yaml"
      http/get
      :body
      (parse #{"/libpod"}))

  (-> "https://docs.docker.com/engine/api/v1.41.yaml"
      http/get
      :body
      (parse #{})))
