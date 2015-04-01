(ns com.danieldyba.fellowship-one.xml.elements
  (:require [clj-time.format :as f]
            [clj-time.core :as t]
            [clojure.xml :as xml]
            [clojure.data.zip.xml :as zx]
            [com.danieldyba.fellowship-one.contribution-types :refer [show-type]]
            [com.danieldyba.fellowship-one.funds :refer [show-fund]]
            [clojure.zip :as zip]))


