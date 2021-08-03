## conta*j*ners [![](https://github.com/lispyclouds/contajners/workflows/Tests/badge.svg)](https://github.com/lispyclouds/contajners/actions?query=workflow%3ATests)

[![License: LGPL v3](https://img.shields.io/badge/license-MIT-blue.svg?style=flat)](https://choosealicense.com/licenses/mit/)
[![project chat](https://img.shields.io/badge/slack-join_chat-brightgreen.svg)](https://clojurians.slack.com/messages/C0PME9N9X)

An idiomatic, data-driven, REPL friendly Clojure client for [OCI](https://opencontainers.org/) complaint container engines inspired from Cognitect's AWS [client](https://github.com/cognitect-labs/aws-api).

**The README here is for the current main branch and _may not reflect the released version_.**

**Please raise issues here for any new feature requests!**

### Status
Work in progress.

### Supported Engines
- [Docker](https://www.docker.com/)
- [Podman](https://podman.io/)

### Installation
TBD

### Build Requirements
- JDK 8+
- Clojure [tools.deps](https://clojure.org/guides/getting_started)

### Running tests locally
- `clojure -M:test` to run all tests.

### The API
This uses the HTTP REST API from the container engines to run. See the API docs for more details:
- Docker [docs](https://docs.docker.com/engine/api/)
- Podman [docs](https://docs.podman.io/en/latest/Reference.html)

#### How does it work?
- The Swagger 2.0 specs are downloaded and parsed into relevant [edn](https://github.com/edn-format/edn) files
- They are categorized by engine type and stored into the resources with version numbers
- The code doing this can be found [here](https://github.com/lispyclouds/contajners/blob/main/fetch_api/main.clj)
- The library is then published with only the parsed and optimized edn to be as lean as possible at runtime have optimal performance
- The main code then loads the corresponding edn file by version and performs the necessary calls to the engine

### Usage

```clojure
(require '[contajners.core :as c])
```

This library aims to be a _as thin layer as possible_ between you and the container engine. This consists of following public functions:

#### categories

Lists the categories of operations supported.
```clojure
(c/categories :docker "v1.41") ; for docker

(c/categories :podman "v3.2.3") ; for podman

#_=> (:system
      :exec
      :images
      :secrets
      :events
      :_ping
      :containers
      :auth
      :tasks
      :volumes
      :networks
      :build
      :nodes
      :commit
      :plugins
      :info
      :swarm
      :distribution
      :version
      :services
      :configs
      :session)
```

Some engines like podman support multiple APIs, for example it supports both podman and docker APIs.
Hence the categories would be namespaced to show which belong to what engine.
eg. `:containers` for docker and `:libpod/containers` for podman APIs.

#### client

Connect to the [UNIX socket](https://en.wikipedia.org/wiki/Unix_domain_socket) and
create a client scoped to the operations of a given category.
```clojure
(def images (c/client {:engine   :podman
                       :category :libpod/images
                       :conn     {:uri "unix:///var/run/podman.sock"}
                       :version  "v3.2.3"}))
```
Using a timeout for the connections.
Some actions can take quite a long time so set the timeout accordingly.
When you don't provide timeouts then there will be no timeout on the client side.
```clojure
(def ping (c/client {:engine   :docker
                     :category :_ping
                     :version  "v1.41"
                     :conn     {:uri             "unix:///var/run/docker.sock"
                                :connect-timeout 10
                                :read-timeout    30000
                                :write-timeout   30000
                                :call-timeout    30000}}}))
```
Alternatively if connecting to a remote engine over TCP supply the `:uri` as `http://your.engine.host:2376`.
**NOTE**: `unix://`, `http://`, `tcp://` and `https://` are the currently supported protocols.

#### ops
Lists the supported ops by a client.
```clojure
(c/ops images)

#_=> (:ImageDeleteLibpod
      :ImagePushLibpod
      :ImageExportLibpod
      :ImageHistoryLibpod
      :ImageDeleteAllLibpod
      :ImageInspectLibpod
      :ImageGetLibpod
      :ImageUntagLibpod
      :ImageTagLibpod
      :ImageChangesLibpod
      :ImageListLibpod
      :ImageLoadLibpod
      :ImageTreeLibpod
      :ImagePruneLibpod
      :ImageSearchLibpod
      :ImageImportLibpod
      :ImageExistsLibpod
      :ImagePullLibpod)
```

#### doc
Returns the summary and the doc URL of an operation in a client.
```clojure
(c/doc images :ImageList)

#_=> {:summary
      "List Images\nReturns a list of images on the server. Note that it uses a different, smaller representation of an image than inspecting a single image.",
      :doc-url "https://docs.docker.com/engine/api/v1.41/#operation/ImageList"}
```

#### invoke
Invokes an operation via the client and a given operation map and returns the result data.
```clojure
; Pulls the busybox:musl image from Docker hub
(c/invoke images {:op     :ImageCreate
                  :params {:fromImage "busybox:musl"}})

; Creates a container named conny from it
(c/invoke containers {:op     :ContainerCreate
                      :params {:name "conny"}
                      :data   {:Image "busybox:musl"
                               :Cmd   "ls"}})
```

The operation map is of the following structure:
```clojure
{:op     :NameOfOp
 :params {:param-1 "value1"
          :param-2 true}
 :data  {map or stream to be passed as a request body}}
```
Takes an optional key `as`. Defaults to `:raw`. Returns an InputStream if passed as `:stream`, the raw underlying network socket if passed as `:socket`. `:stream` is useful for streaming responses like logs, events etc, which run till the container is up. `:socket` is useful for events when bidirectional streams are returned by docker in operations like `:ContainerAttach`.
```clojure
{:op     :NameOfOp
 :params {:param-1 "value1"
          :param-2 true}
 :as     :stream}
```

Takes another optional key `:throw-exceptions`. Defaults to `false`. If set to true will throw an exception for exceptional status codes from the Docker API i.e. `status >= 400`. Throws an `java.lang.RuntimeException` with the message.
```clojure
{:op               :NameOfOp
 :throw-exception? true}
```

### General guidelines
TBD

## License

Copyright © 2021 Rahul De.

Distributed under the MIT License. See LICENSE.
