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
    (with-open [rdr (clojure.java.io/reader stream)]
      (loop [r (java.io.BufferedReader. rdr)]
        (when-let [line (.readLine r)]
          (reaction-fn line)
          (recur r))))))

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

### Not so common scenarios

#### Accessing undocumented/experimental APIs
There are some cases where you may need access to an API that is either experimental or is not in the swagger docs.
Docker [checkpoint](https://docs.docker.com/engine/reference/commandline/checkpoint/) is one such example. Thanks [@mk](https://github.com/mk) for bringing it up!

Since this uses the published APIs from the swagger spec, the way to access them is to use the lower level fn `fetch` from the `contajners.impl` ns. The caveat is the **response will be totally raw(data, stream or the socket itself)**.

**Warning**: fns from the `impl` ns are not guaranteed to be stable as they are internal.

client method path headers query-params body as throw-exceptions throw-entire-message

fetch takes the following params as a map:
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
(require '[contajners.impl :as impl])
(require '[unixsocket-http.core :as http])

;; This is the undocumented API in the Docker Daemon.
;; See https://github.com/moby/moby/pull/22049/files#diff-8038ade87553e3a654366edca850f83dR11
(impl/fetch {:conn   (http/client {:uri "unix:///var/run/docker.sock"})
             :path   "/v1.41/containers/conny/checkpoints"
             :method :get})
```

More examples of low level calls:
```clojure
;; Ping the server
(impl/fetch {:conn   (http/client {:uri "unix:///var/run/docker.sock"})
             :path   "/v1.41/_ping"
             :method :get})

;; Copy a folder to a container
(impl/fetch {:conn   (http/client {:uri "unix:///var/run/docker.sock"})
             :method :put
             :path   "/v1.41/containers/conny/archive"
             :query  {:path "/root/src"}
             :body   (-> "src.tar.gz"
                         io/file
                         io/input-stream)})
```

#### Reading a streaming output in case of an exception being thrown

When `:throw-exceptions` is passed as `true` and the `:as` is set to `:stream`, to read the response stream, pass `throw-entire-message` as `true` to the invoke. The stream is available as `:body` in the ex-data of the exception.
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

And anything else is possible!
