(defproject com.danieldyba/fellowship-one "0.0.4-SNAPSHOT"
  :description "An API wrapper for Fellowship One"
  :url "https://github.com/dyba/fellowship-one"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-http "0.9.2"]
                 [clj-oauth "1.5.1"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [clj-time "0.9.0"]
                 [pandect "0.3.4"]
                 [environ "1.0.0"]
                 [ring/ring-core "1.3.1"]]
  :deploy-repositories [["releases" :clojars]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]]
                   :plugins [[lein-midje "3.1.3"]]}})
