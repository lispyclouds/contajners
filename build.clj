(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'org.clojars.lispyclouds/contajners)

(def version "1.0.7")

(def class-dir "target/classes")

(def basis (b/create-basis {:project "deps.edn"}))

(def jar-file (format "target/%s-%s.jar" (name lib) version))

(def src-dirs ["src" "resources"])

(defn clean
  [_]
  (b/delete {:path "target"}))

(defn jar
  [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs src-dirs
                :pom-data [[:url "https://github.com/lispyclouds/contajners"]
                           [:developers
                            [:developer [:name "Rahul De"]]]
                           [:scm
                            [:url "https://github.com/lispyclouds/contajners"]]
                           [:licenses
                            [:license
                             [:name "MIT"]
                             [:url "https://opensource.org/license/mit"]
                             [:distribution "repo"]]]]})
  (b/copy-dir {:src-dirs src-dirs
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn deploy
  [_]
  (dd/deploy {:installer :remote
              :sign-releases? true
              :artifact jar-file
              :pom-file (b/pom-path {:lib lib
                                     :class-dir class-dir})}))
