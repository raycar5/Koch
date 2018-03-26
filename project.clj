(defproject koch "0.1.1"
  :description "Simple state management for reagent"
  :url "https://github.com/raycar5/Koch"
  :license {:name "MIT"}
  :plugins [[lein-cljsbuild "1.1.6"]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.908"]
                 [reagent "0.7.0"]
                 [org.clojure/core.async "0.3.465"]
                 [cljsjs/proptypes "0.14.3-0"]]

  :profiles {:dev {:dependencies [[binaryage/devtools "0.9.9"]]
                   :plugins [[lein-doo "0.1.7"]]}}
  :cljsbuild {:builds {:minify {:source-paths ["src"]
                                :compiler {:optimizations :advanced
                                           :pretty-print false}}
                       :dev {:source-paths ["src"]
                             :compiler {:optimizations :whitespace}}
                       :test {:id "test"
                              :source-paths ["src" "test"]
                              :compiler {:output-to "compiled/cljs-tests.js"
                                         :output-dir "compiled/test"
                                         :main koch.runner
                                         :optimizations :none}}}})
