(set-env! :dependencies '[[org.clojure/clojure "1.8.0"]
                          [org.clojure/clojurescript "1.9.562"]
                          [org.clojure/core.async "0.3.443"]
                          [org.clojure/tools.cli "0.3.5"]
                          [cljsjs/nodejs-externs "1.0.4-1"]
                          [adzerk/boot-cljs "2.0.0"]
                          [cider/piggieback "0.3.9"]
                          [org.clojure/tools.nrepl "0.2.13"]]
          :source-paths #{"src"})

(require 'boot.repl
         '[adzerk.boot-cljs :refer [cljs]]
         '[clojure.java.io :as io]
         '[clojure.java.shell :as shell])

(task-options!
 cljs {:compiler-options {:target :nodejs
                          :optimizations :advanced
                          :externs ["src/externs/google.js"]}})

(swap! boot.repl/*default-middleware* conj 'cider.piggieback/wrap-cljs-repl)

(deftask npm
  "Install NPM dependencies."
  []
  (println (:out (shell/sh "npm" "install")))
  identity)

(deftask rename
  []
  (with-post-wrap _
    (.renameTo (io/file "target" "main.js") (io/file "gcal-to-org.js"))))

(deftask build
  "Build gcal-to-org cli app."
  []
  (comp (npm) (cljs) (target) (rename)))
