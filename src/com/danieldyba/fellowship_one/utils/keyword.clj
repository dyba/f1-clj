(ns com.danieldyba.fellowship-one.utils.keyword
  (:require [clojure.string :as str]
            [com.danieldyba.fellowship-one.utils.string :as s]))

(defn keyword->str [k]
  (str/replace (str k) ":" ""))

(defn camel-case [k]
  (-> k
      keyword->str
      s/camel-case
      keyword))

(defn lower-case [k]
  (-> k
      keyword->str
      str/lower-case
      keyword))

(defn upper-case [k]
  (-> k
      keyword->str
      str/upper-case
      keyword))
