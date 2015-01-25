(ns com.danieldyba.fellowship-one.funds
  (:require [clj-http.client :as client]
            [com.danieldyba.fellowship-one.utils.http :refer [api-action]]))

(defn list-funds []
  (api-action :GET "/giving/v1/funds" {:accept :xml}))

(defn show-fund [id]
  (let [path (str "/giving/v1/funds/" id)]
    (api-action :GET path {:accept :xml})))
