(ns f1-clj.batches
  (:require [clj-http.client :as client]
            [f1-clj.utils.keyword :as k]
            [f1-clj.utils.string :as s]
            [f1-clj.utils.http :refer [api-action]]))

(defn new-batch
  "Returns the template for a new batch."
  []
  (api-action :GET "/giving/v1/batches/new" {:accept :xml}))

(defn edit-batch
  "Expects the id of a batch. Retrieves the batch in its most recent condition with its
  latest values."
  [id]
  (api-action :GET (str "/giving/v1/batches/" id "/edit") {:accept :xml}))

(defn create-batch
  "Expects an XML payload to send to the server."
  [body]
  (api-action :POST
              "/giving/v1/batches"
              {:body body
               :content-type :xml
               :accept :xml}))

(defn put-batch
  "Updates a single batch."
  [id]
  (api-action :PUT (str "/giving/v1/batches/" id) {:accept :xml}))

(defn show-batch
  "Expects the id of a batch. Returns the batch with that id."
  [id]
  (api-action :GET (str "/giving/v1/batches" id) {:accept :xml}))

(defn search-batches
  [params]
  (let [normalized-params (reduce (fn [acc entry]
                                    (let [[k v] entry
                                          param (-> k k/keyword->str s/camel-case keyword)]
                                      (assoc acc param v)))
                                  {}
                                  params)]
    (api-action :GET "/giving/v1/batches/search" {:accept :xml :query-params normalized-params})))


