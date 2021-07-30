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
TBD

## License

Copyright © 2021 Rahul De and [contributors](https://github.com/lispyclouds/contajners/graphs/contributors).

Distributed under the MIT License. See LICENSE.
