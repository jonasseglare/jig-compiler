(defproject jig-compiler "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clj-http "3.10.0"]
                 [hickory "0.7.1"]
                 [com.cemerick/url "0.1.1"]
                 [cheshire "5.9.0"]]
  :main jig-compiler.core
  :repl-options {:init-ns jig-compiler.core})
