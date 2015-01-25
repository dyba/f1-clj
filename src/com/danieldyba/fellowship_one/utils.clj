(ns com.danieldyba.fellowship-one.utils
  (:require [clojure.string :as str]))

(defn camel-case
  [str]
  (str/replace str #"-(\w)" #(.toUpperCase (%1 1))))

(defn keyword->str
  [k]
  (str/replace (str k) ":" ""))
