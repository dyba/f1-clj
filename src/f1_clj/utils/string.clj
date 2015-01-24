(ns f1-clj.utils.string
  (:require [clojure.string :as str]))

(defn camel-case
  [str]
  (str/replace str #"-(\w)" #(.toUpperCase (%1 1))))
