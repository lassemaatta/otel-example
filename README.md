# otel-example

Example of how enabling the instrumentation agent causes problems with nested spans.

This is a clojure project running on leiningen, so you need at least
- a JVM, 17+ ought to be enough
- clojure: https://clojure.org/guides/install_clojure
- leiningen: https://leiningen.org/

## Usage

run `lein run` to start app. A web server will be started in `http://localhost:8080`.

the `run_jaeger.sh` script can be used to start jaeger, access the UI with `http://localhost:16686`

## License

Copyright © 2025 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
