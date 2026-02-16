## General Guidelines

### Sample code for common scenarios

#### Pulling an image
```clojure
(def images-docker (c/client {:engine   :docker
                              :category :images
                              :version  "v1.41"
                              :conn     {:uri "unix:///var/run/docker.sock"}}))

(c/invoke images-docker {:op     :ImageCreate
                         :params {:fromImage "busybox:musl"}})
```

#### Building an image with Docker

Docker builds an image within a context, ie a set of files. The docker API
requires a tar file as the body of the request. As of 2022-02-04, the header
field `Content-type` is not filled by its default value
`application/x-tar`, so it has to be added manually.

Thus, to build an image, a user needs to create a tar with all the relevant
files for building it and also add the `:Content-type` field in the query
params.

Given the following `Dockerfile`

``` dockerfile
FROM docker.io/amazoncorretto:17-alpine3.15-jdk
COPY app.jar .
CMD ["java", "-jar", "app.jar", "init"]
```

and an arbitrary jar `app.jar` at the root of your project, the following
script would build the `contajners-build-example` image.

```clojure
(require '[babashka.process :as process])
(require '[cheshire.core :as json])
(require '[clojure.pprint :as pprint])

(def build
  (c/client {:engine   :docker
             :category :build
             :version  "v1.41"
             :conn     {:uri "unix:///var/run/docker.sock"}}))

(defn ->tar!
  []
  ;; gather the files in the tar file
  (process/sh ["tar" "-czvf" "docker.tar.gz"
               "app.jar"
               "Dockerfile"])
  ;; prints the content of the tar file
  (process/sh ["tar" "tvf" "docker.tar.gz"]
              {:out *out*}))

(defn build-cmd!
  []
  (c/invoke
    build
    {:op     :ImageBuild
     :params {:t            "contajners-build-example"
              :Content-type "application/x-tar"} ;; Add the header here.
     :data   (io/input-stream "docker.tar.gz")
     :as     :stream}))

(defn show-build-output!
  [input-stream]
  (let [stream-data (json/parsed-seq (io/reader input-stream))]
    (loop [data stream-data]
      (when-let [line (first data)]
        (if-let [s (get line "stream")]
          (do
            (print s)
            (flush))
          (pprint/pprint line))
        (recur (rest data))))))

(defn build!
  [& {:keys [verbose?]}]
  (let [docker-output-stream (build-cmd!)]
    (when verbose?
      (show-build-output! docker-output-stream))))

(->tar!)
(->build! {:verbose? true})
; (->build! :verbose? true) ;; using Clojure 1.11+
```
Thanks [@davidpham87](https://github.com/davidpham87) for this example!

#### Creating a container
```clojure
(def containers-docker (c/client {:engine :docker
                                  :category :containers
                                  :conn     {:uri "unix:///var/run/docker.sock"
                                  :version  "v1.41"}}))

(c/invoke containers-docker {:op     :ContainerCreate
                             :params {:name "conny"}
                             :data   {:Image "busybox:musl"
                                      :Cmd   ["sh"
                                              "-c"
                                              "i=1; while :; do echo $i; sleep 1; i=$((i+1)); done"]}})
```

#### Starting a container
```clojure
(c/invoke containers-docker {:op     :ContainerStart
                             :params {:id "conny"}})
```

#### Creating a network
```clojure
(def networks-docker (c/client {:engine   :docker
                                :category :networks
                                :conn     {:uri "unix:///var/run/docker.sock"}
                                :version  "v1.41"}))

(c/invoke networks-docker {:op   :NetworkCreate
                           :data {:Name "conny-network"}})
```

#### Streaming logs
```clojure
; fn to react when data is available
(defn react-to-stream
  [stream reaction-fn]
  (future
    (with-open [r (clojure.java.io/reader stream)]
      (loop []
        (when-let [line (.readLine r)]
          (reaction-fn line)
          (recur))))))

(def log-stream (c/invoke containers-docker {:op     :ContainerLogs
                                             :params {:id     "conny"
                                                      :follow true
                                                      :stdout true}
                                             :as     :stream}))

(react-to-stream log-stream println) ; prints the logs line by line when they come.
```

#### Attach to a container and send data to stdin
*Note:* `:as :socket` applies only to the JVM runtime.
```clojure
;; This is a raw bidirectional java.net.Socket, so both reads and writes are possible.
;; conny-reader has been started with: docker run -d -i --name conny-reader alpine:latest sh -c "cat - >/out"
(def sock (c/invoke containers {:op     :ContainerAttach
                                :params {:id     "conny-reader"
                                         :stream true
                                         :stdin  true}
                                :as     :socket}))

(clojure.java.io/copy "hello" (.getOutputStream sock))

(.close sock) ; Important for freeing up resources.
```

#### Using registries that need authentication
Thanks [@AustinC](https://github.com/AustinC) for this example.

```clojure
(ns dclj.core
  (:require [contajners.core :as c]
            [cheshire.core :as json])
  (:import [java.util Base64]))

(defn b64-encode
  [to-encode]
  (.encodeToString (Base64/getEncoder) (.getBytes to-encode)))

(def auth
  (-> {"username"      "un"
       "password"      "pw"
       "serveraddress" "docker.acme.com"}
      json/encode
      b64-encode))

(def images
  (c/client {:engine   :docker
             :category :images
             :conn     {:uri "unix:///var/run/docker.sock"}
             :version  "v1.41"}))

(c/invoke images
          {:op               :ImageCreate
           :params           {:fromImage       "docker.acme.com/eg:2.1.995"
                              :X-Registry-Auth auth}
           :throw-exceptions true})
```

#### HTTPS and Mutual TLS(mTLS)

Since both https and unix sockets are suppported, and generally docker deamons exposed over HTTPS are protected via [mTLS](https://docs.docker.com/engine/security/protect-access/#use-tls-https-to-protect-the-docker-daemon-socket), here is an example using mTLS to connect to docker via HTTPS:

```clojure
;; Create a client using https
;; The ca.pem, key.pem and cert.pem are produced by the docker daemon when protected via mTLS
(def http-tls-ping
  (c/client {:category :_ping
             :engine   :docker
             :version  "v1.41"
             :conn     {:uri  "https://my.remote.docker.host:8000"
                        :mtls {:ca   "ca.pem"
                               :key  "key.pem"
                               :cert "cert.pem"}}}))

(invoke http-tls-ping {:op :SystemPing}) ;; => Returns "OK"
```

The caveat here is _password protected PEM files aren't supported yet_. Please raise an issue if there is a need for it.

#### Supplying multiple values to a single param

There are cases where you may need to send multiple values to a single param, for example:
- [ContainersStatsAllLibpod](https://docs.podman.io/en/latest/_static/api.html#operation/ContainersStatsAllLibpod)
- [ImageCommitLibpod](https://docs.podman.io/en/latest/_static/api.html#operation/ImageCommitLibpod)

Whenever the docs mention about an `Array of <type>` you can send a collection of those values to that param.
```clojure
(c/invoke containers-podman
          {:op     :ContainersStatsAllLibpod
           :params {:containers ["id1" "name1" "id2"]}})
```

Thanks [@leahneukirchen](https://github.com/leahneukirchen) for this!

#### Get an archive of a filesystem resource in a container
Get a tar archive of a resource in the filesystem of container id.

```clojure
(c/invoke containers-docker
          {:op                   :ContainerArchive
           :params               {:id   "conny"
                                  :path "/root/src"}
           :as                   :stream
           :throw-exceptions     true
           :throw-entire-message true})
```

#### Extract an archive of files or folders to a directory in a container
Upload a tar archive to be extracted to a path in the filesystem of container id. `path` parameter is asserted to be a directory.

```clojure
(c/invoke containers-docker
          {:op                   :PutContainerArchive
           :params               {:id   "conny"
                                  :path "/root/src"}
           :data                 (->  "src.tar.gz"
                                      io/file
                                      io/input-stream)
           :as                   :stream
           :throw-exceptions     true
           :throw-entire-message true})
```
Thanks [@rafaeldelboni](https://github.com/rafaeldelboni) for this!

### Not so common scenarios

#### Accessing undocumented/experimental APIs
There are some cases where you may need access to an API that is either experimental or is not in the swagger docs.
Docker [checkpoint](https://dcs.docker.com/engine/reference/commandline/checkpoint/) is one such example. Thanks [@mk](https://github.com/mk) for bringing it up!

Since this uses the published APIs from the swagger spec, the way to access them is to use the lower level fn `request` from either the `contajners.jvm-runtime` or `contajners.sci-runtime` ns. The caveat is the **response will be totally raw(data, stream or the socket itself)**.

**Warning**: fns from the `impl` and `rt` ns are not guaranteed to have a stable API as they are internal.

client method path headers query-params body as throw-exceptions throw-entire-message

`request` takes the following params as a map:
- client: the connection. Required.
- path: the relative path to the operation. Required.
- method: the method of the HTTP request as a keyword. Required.
- query-params: the map of key-values to be passed as query params.
- header: the map of key-values to be passed as HEADER params.
- body: the stream or map(will be converted to JSON) to be passed as body.
- as: takes the kind of response expected. One of :stream, :socket or :data. Same as `invoke`. Default: `:data`.
- throw-exceptions: Throws exceptions when status is >= 400 for API calls. Default: false.
- throw-entire-message: Includes the full exception as a string. Default: false.

```clojure
(require
  #?(:bb '[contajners.sci-runtime :as rt]
     :clj '[contajners.jvm-runtime :as rt]))

;; This is the undocumented API in the Docker Daemon.
;; See https://github.com/moby/moby/pull/22049/files#diff-8038ade87553e3a654366edca850f83dR11
(rt/request {:client (rt/client "unix:///var/run/docker.sock" {})
             :path   "/v1.41/containers/conny/checkpoints"
             :method :get})
```

More examples of low level calls (these are not experimental, just here to demo the low level API interactions):
```clojure
;; Ping the server
(rt/request {:client (rt/client "unix:///var/run/docker.sock" {})
             :path   "/v1.41/_ping"
             :method :get})

;; Copy a folder to a container
(rt/request {:client (rt/client "unix:///var/run/docker.sock" {})
             :method :put
             :path   "/v1.41/containers/conny/archive"
             :query-params  {:path "/root/src"}
             :body   (-> "src.tar.gz"
                          io/file
                          io/input-stream)})
```

#### Reading a streaming output in case of an exception being thrown

When `:throw-exceptions` is passed as `true` and the `:as` is set to `:stream`, to read the response stream, pass `throw-entire-message` as `true` to the invoke. The stream is available as `:body` in the ex-data of the exception. `throw-entire-message` is only applicable to the JVM runtime.
```clojure
(try
  (invoke containers-docker
          {:op                    :ContainerArchive
           :params                {:id   "conny"
                                   :path "/this-does-not-exist"}
           :as                    :stream
           :throw-exceptions      true
           :throw-entire-message  true})
  (catch Exception e
    (-> e ex-data :body slurp println))) ; Prints out the body of error from docker.
```

#### Using the Podman specific API

Podman via its REST interface supports both the docker compatible API as well as its own specific one. When using Podman as the driver, use `:libpod/<category>` instead of `:<category>` to access that.

Example:
```clojure
; Docker compatible API
(def dnetworks (c/client {:engine   :podman
                          :category :networks
                          :conn     {:uri "http://localhost:8080"}
                          :version  "v4.4.4"}))

; Podman specific API
(def pnetworks (c/client {:engine   :podman
                          :category :libpod/networks
                          :conn     {:uri "http://localhost:8080"}
                          :version  "v4.4.4"}))
```

And anything else is possible!

#### Connect to docker socket on remote hosts

We are going to talk about connecting to a remote Docker endpoint using SSH.
This guide is for babashka, but should be easy to adapt for clojure/nbb.

For local unix socket connections to Docker endpoint, you can use the path to the file (unix socket, e.g. `unix:///var/run/docker.sock`).
But sometimes you have a swarm cluster / Docker node on remote hosts.
Accessing the docker endpoint remotely involves using either an ssh connection or exposing Docker endpoint as a TCP service (preferably using TLS certificates).
Most people avoid dealing with TLS certificates becasue the process can be quite complicated.
So they opt to expose Docker endpoint via unix sockets (which is not exposed via internet) and provide access via SSH to the node. See https://docs.docker.com/config/daemon/remote-access/ for more details.

docker-cli has a feature called [docker contexts](https://docs.docker.com/engine/context/working-with-contexts/) that allows users to access remote Docker daemons using ssh and unix sockets.

The process relies on using SSH local port forwarding (`man ssh`).
* You open an SSH connection to a remote host and also bind a local port / unix socket.
* Clients connect to local port / unix socket and SSH will forward all information to remote port / unix socket.

Using ssh command it looks like this:
```shell
ssh dev1.example.com -L /tmp/my-local-docker.sock:/var/run/docker.sock
```
Once you run this you can connect to unix socket `/tmp/my-local-docker.sock` on your local machine and traffic will
be directed to the remote host over an encrypted channel.

Using contajners with babashka and [bbssh](https://github.com/epiccastle/bbssh) we can achieve the same thing.

```clojure
#!/usr/bin/env bb
(ns bb-ssh
  (:require [babashka.pods :as pods]
            [babashka.deps :as deps]))

;; dynamically add bbssh and contajners to bb classpath
(pods/load-pod 'epiccastle/bbssh "0.5.0")
(deps/add-deps '{:deps {org.clojars.lispyclouds/contajners {:mvn/version "1.0.8"}}})

;; require the ns that we need from the libs
(require '[pod.epiccastle.bbssh.core :as bbssh]
         '[contajners.core :as c])

;; notice this is a rich comment
(comment

  ;; check bbssh for more options and configurations
  (let [port 38021
        session (bbssh/ssh "dev1.example.com"
                           {:username "ubuntu"
                            :identity "/home/ieugen/.ssh/id_ed25519"
                            :bind-address "127.0.0.1"        ;; address to bind to on the local machine
                            :local-port  port              ;; the port to lisen on locally
                            :remote-unix-socket "/var/run/docker.sock"  ;; unix socket on the remote network
                            :connect-timeout 30000         ;; timeout for the remote connection (ms)
                            })
        images-docker (c/client {:engine   :docker
                                 :category :images
                                 :version  "v1.42"
                                 :conn     {:uri (str "http://127.0.0.1:" port)}})]
    (println "Remote unix socket available as TCP port" port)
    ;; (println images-docker)
    (println (c/invoke images-docker {:op :ImageList})))
  )
```

Original issue for this feature in [bbssh](https://github.com/epiccastle/bbssh/issues/12).

Thanks [@ieugen](https://github.com/ieugen) for this example!

### Adding support for a new container engine

contajners has been designed keeping in mind that support for newer and upcoming engines should be simple enough to add. The only requirement from the engine is that it must expose a REST API and has some form of Swagger/OpenAPI docs available.

Currently only Swagger 2.0 parsing is there as thats whats necessary now however its easy to add support for OpenAPI 3.0+. Please raise an issue when necessary.

- Clone the repository
- Make sure JDK 19+ is installed
- Navigate to `fetch_api/main.clj`
- In the `sources` map add the name of the engine as a key
  - For the value, add the `:url` template from where the api yaml can be downloaded
  - add the `doc-url` template where the doc of a version and OperationId can be seen
  - add the list of available `:versions` which would be used in the template urls
- You can add an optional `:namespace` which makes the category available namespaced, for example `:libpod/containers` for podman and `:containers` for docker. This is useful for APIs exposing similar functionality but are compatible with other engines. Like Podman is with the Docker API.
- Run `clojure -J--enable-preview -X:fetch-api` from the root of the repo to download and parse all the APIs into the optimized edn files in `resources/contajners/<the engine name>/<version>.edn`
- When creating the client with `contajners.core/client` the new engine should be available and can pass it as `:the engine name` as a keyword. Similarly `contajners.core/categories` should work as well
- Also consider contributing it back here, we all would LOVE to use _that shiny new engine_ 😍
