(ns f1-clj.funds
  (:require [clj-http.client :as client]
            [f1-clj.utils.http :refer [api-action]]))

(defn list-funds []
  (api-action :GET "/giving/v1/funds" {:accept :xml}))

(defn show-fund [id]
  (let [path (str "/giving/v1/funds/" id)]
    (api-action :GET path {:accept :xml})))
