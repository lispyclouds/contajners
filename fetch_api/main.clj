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
            :namespaces #{"/libpod"}
            :versions   ["v3.1.0"
                         "v3.1.1"
                         "v3.1.2"
                         "v3.2.0"
                         "v3.2.1"
                         "v3.2.2"]}})

(defn find-first
  [pred coll]
  (some #(when (pred %) %) coll))

;; TODO: Better?
(defn ->category
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

(defn ->params
  [^Parameter param]
  {:name     (.getName param)
   :in       (keyword (.getIn param))
   :required (or (.getRequired param) false)
   :schema   (-> param
                 .getSchema
                 .getType)})

(defn ->operation-info
  [path method ^Operation operation]
  {(keyword (.getOperationId operation)) {:summary (.getSummary operation)
                                          :method  (-> method
                                                       str
                                                       keyword)
                                          :path    path
                                          :params  (map ->params (.getParameters operation))}})

(defn ->operation
  [namespaces path ^PathItem path-info]
  (let [operations (.readOperationsMap path-info)]
    (->> operations
         (map #(->operation-info path (key %) (val %)))
         (map #(hash-map (->category path namespaces) %)))))

(defn parse
  [namespaces ^String spec]
  (let [parse-options (doto (ParseOptions.)
                        (.setResolveFully true))]
    (->> (.readContents (OpenAPIParser.) spec nil parse-options) ; specs are still Swagger 2.0 🙄
         (.getOpenAPI)
         (.getPaths)
         (mapcat #(->operation namespaces (key %) (val %)))
         (apply (partial merge-with into)))))

(defn write-api
  [path content]
  (when-not (.exists (io/file path))
    (io/make-parents path))
  (with-open [w (io/writer path)]
    (binding [*print-length* false
              *out* w]
      (pr content))))

;; TODO: Download in async
(defn dowload-and-process
  [engine url-format version namespaces]
  (println (format "Downloading version %s from %s" version url-format))
  (let [{:keys [status body]} (http/get (format url-format version))]
    (if (>= status 400)
      (throw (RuntimeException. (format "Error fetching version %s: %s"
                                        version
                                        body)))
      (->> body
           (parse namespaces)
           (write-api (format "resources/contajners/%s/%s.edn"
                              (name engine)
                              version))))))

(defn run
  [& _]
  (let [download-info (for [[engine {:keys [url namespaces versions]}] sources
                            version versions]
                        [engine url version namespaces])]
    (pmap #(apply dowload-and-process %) download-info)))

(comment
  (set! *warn-on-reflection* true)

  (run)

  (find-first #{3} [1 2 3 4 5])

  (->category "/libpod/containers" #{"/libpod"})

  (->category "/containers/json" #{})

  (write-api "resources/contajners/docker/blah.edn" {:a 42})

  (dowload-and-process :docker
                       "https://docs.docker.com/engine/api/v%s.yaml"
                       "v1.41"
                       #{})

  (dowload-and-process :podman
                       "https://storage.googleapis.com/libpod-master-releases/swagger-v3.2.2.yaml"
                       "v3.2.2"
                       #{"/libpod"})

  (->> "https://storage.googleapis.com/libpod-master-releases/swagger-v3.2.2.yaml"
       http/get
       :body
       (parse #{"/libpod"}))

  (->> "https://docs.docker.com/engine/api/v1.41.yaml"
       http/get
       :body
       (parse #{})))
