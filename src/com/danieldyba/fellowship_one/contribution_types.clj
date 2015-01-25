(ns com.danieldyba.fellowship-one.contribution-types
  (:require [clj-http.client :as client]
            [com.danieldyba.fellowship-one.utils.http :refer [api-action]]))

(defn list-types
  "Return a list of contribution types."
  []
  (api-action :GET "/giving/v1/contributiontypes" {:accept :xml}))

(defn show-type
  "Return a single contribution type for the given id."
  [id]
  (let [path (str "/giving/v1/contributiontypes/" id)]
    (api-action :GET path {:accept :xml})))
