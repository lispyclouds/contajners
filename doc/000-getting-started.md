## Installation

#### Clojure CLI/deps.edn
```clojure
{org.clojars.lispyclouds/contajners {:mvn/version "1.0.0"}}
```
as a gitlib (prefer this method as new APIs are added more frequently than this is released on clojars):
```
{io.github.lispyclouds/contajners {:git/sha "<COMMIT ID HERE>"}}
```

#### Leiningen/Boot
```clojure
[org.clojars.lispyclouds/contajners "1.0.0"]
```

#### Gradle
```groovy
implementation "org.clojars.lispyclouds/contajners:1.0.0"
```

#### Maven
```xml
<dependency>
  <groupId>org.clojars.lispyclouds</groupId>
  <artifactId>contajners</artifactId>
  <version>1.0.0</version>
</dependency>
```

#### Babashka
add at runtime:

```clojure
(require '[babashka.deps :as deps])

; From maven
(deps/add-deps '{:deps {org.clojars.lispyclouds/contajners {:mvn/version "1.0.0"}}})

; From Github
(deps/add-deps '{:deps {io.github.lispyclouds/contajners {:git/sha "<COMMIT ID HERE>"}}})
```

via [bb.edn](https://book.babashka.org/#_bb_edn)

```clojure
; Maven
{:deps {org.clojars.lispyclouds/contajners {:mvn/version "1.0.0"}}}

; Github
{:deps {io.github.lispyclouds/contajners {:git/sha "<LATEST COMMIT ID HERE>"}}}
```

*Note: [curl](https://curl.se/download.html) needs to be present on the PATH to be used from Babashka.*

## Usage

### Supported Engines

Currently the following engines are supported, make sure at least one of them are available.
- [Docker](https://www.docker.com/)
- [Podman](https://podman.io/)

Checkout the [API](/doc/001-api.md) for more details.

### An example REPL session

If using docker, the service is assumed to be running on `unix:///var/run/docker.sock`

If using podman, the service is assumed to be running on `http://localhost:8080`

NOTE: Advanced tip: When using docker, you can access a unix socket on a remote server by using SSH local port forwarding (see `man ssh`).
When using `contajners` with `babashka` this can be done via [bbssh](https://github.com/epiccastle/bbssh/issues/12#issuecomment-1586938532).

```clojure
user=> (require '[contajners.core :as c])
nil

user=> (c/categories :docker "v1.41")
(:system
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

user=> (c/categories :podman "v3.2.3")
(:libpod/system
 :system
 :libpod/exec
 :exec
 :images
 :libpod/secrets
 :libpod/images
 :secrets
 :events
 :libpod/events
 :libpod/_ping
 :libpod/generate
 :libpod/manifests
 :containers
 :libpod/containers
 :auth :volumes
 :libpod/volumes
 :libpod/networks
 :networks
 :build
 :libpod/build
 :commit
 :libpod/commit
 :info
 :libpod/info
 :libpod/pods
 :libpod/play
 :libpod/version
 :version)

user=> (def images-docker (c/client {:engine   :docker
                                     :category :images
                                     :version  "v1.41"
                                     :conn     {:uri "unix:///var/run/docker.sock"}}))
#'user/images-docker

user=> (def images-podman (c/client {:engine   :podman
                                     :category :libpod/images
                                     :version  "v3.2.3"
                                     :conn     {:uri "http://localhost:8080"}}))
#'user/images-podman

user=> (c/ops images-docker)
(:ImageList
 :ImageLoad
 :ImagePrune
 :ImagePush
 :ImageHistory
 :ImageDelete
 :ImageCreate
 :ImageGet
 :ImageTag
 :ImageSearch
 :ImageInspect
 :ImageGetAll)

user=> (c/ops images-podman)
(:ImageDeleteLibpod
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

user=> (c/doc images-docker :ImageCreate)
{:summary "Create an image", :doc-url "https://docs.docker.com/engine/api/v1.41/#operation/ImageCreate"}

user=> (c/doc images-podman :ImagePullLibpod)
{:summary "Pull images", :doc-url "https://docs.podman.io/en/v3.2.3/_static/api.html#operation/ImagePullLibpod"}

user=> (c/invoke images-docker {:op     :ImageCreate
                                :params {:fromImage "busybox:musl"}})
{:status "Pulling from library/busybox", :id "musl"}

user=> (c/invoke images-podman {:op     :ImagePullLibpod
                                :params {:reference "alpine:latest"}})
{:stream "Resolved \"alpine\" as an alias (/etc/containers/registries.conf.d/000-shortnames.conf)\n"}

user=> (c/invoke images-docker {:op :ImageList})
[{:RepoDigests
  ["busybox@sha256:88b20a5fa16ff1ce1f2f8aaff1f3e4fbc7376154e2b22b2e53a2b80e48169694"],
  :Labels nil,
  :SharedSize -1,
  :Size 1430648,
  :Id "sha256:9ad2c435a887e3f723654e09b48563de44aa3c7950246b2e9305ec85dd3422db",
  :Containers -1,
  :ParentId "",
  :VirtualSize 1430648,
  :Created 1623097241,
  :RepoTags ["busybox:musl"]}]

user=> (c/invoke images-podman {:op :ImageListLibpod})
[{:RepoDigests
  ["sha256:adab3844f497ab9171f070d4cae4114b5aec565ac772e2f2579405b78be67c96"
   "sha256:1775bebec23e1f3ce486989bfc9ff3c4e951690df84aa9f926497d82f2ffca9d"],
  :Labels nil,
  :SharedSize 0,
  :Size 5869638,
  :Id
  "d4ff818577bc193b309b355b02ebc9220427090057b54a59e73b79bdfe139b83",
  :History ["docker.io/library/alpine:latest"],
  :Containers 0,
  :ParentId "",
  :Digest
  "sha256:adab3844f497ab9171f070d4cae4114b5aec565ac772e2f2579405b78be67c96",
  :Names ["docker.io/library/alpine:latest"],
  :VirtualSize 5869638,
  :Created 1623795577,
  :RepoTags ["docker.io/library/alpine:latest"]}]
```
