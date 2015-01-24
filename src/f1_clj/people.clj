(ns f1-clj.people
  (:require [clj-http.client :as client]
            [f1-clj.utils.keyword :as k]
            [f1-clj.utils.string :as s]
            [f1-clj.utils.http :refer [api-action]]))

(defn content-type-from-version
  [version]
  (if (= (str version) (str "2"))
    "application/vnd.fellowshiponeapi.com.people.people.v2+xml"
    :xml))

(defn search-people
  "Expects a map containing the request parameters with spear-cased keywords. Returns a collection of people
  for the parameters provided."
  [params & [opts]]
  (let [normalized-params (reduce (fn [acc entry]
                                    (let [[k v] entry
                                          param (-> k k/keyword->str s/camel-case keyword)]
                                      (assoc acc param v)))
                                  {}
                                  params)
        content-type (content-type-from-version (:version opts))
        cleaned-opts (dissoc opts :accept :query-params :content-type :version)]
    (api-action :GET
                "/v1/People/Search"
                (merge {:accept :xml
                        :query-params normalized-params
                        :content-type content-type}
                       cleaned-opts))))

(defn show-person
  "Expects the id of a person. Returns a single person with that id."
  [id & opts]
  (let [content-type (content-type-from-version (:version opts))]
    (api-action :GET (str "/v1/People/" id) {:accept :xml :content-type content-type})))

(defn edit-person
  "Expects the id of a person. Returns a single person with that id. Call this before updating
  a person in order to retrieve the resource in its most recent condition with its latest values."
  [id & opts]
  (let [content-type (content-type-from-version (:version opts))]
    (api-action :GET (str "/v1/People/" id "/Edit") {:accept :xml :content-type content-type})))

(defn new-person
  "Returns the template for a new person."
  [& opts]
  (let [content-type (content-type-from-version (:version opts))]
    (api-action :GET "/v1/People/New" {:accept :xml :content-type content-type})))

(defn create-person
  "Expects an XML payload to send to the server."
  [body & opts]
  (let [content-type (content-type-from-version (:version opts))]
    (api-action :POST
                "/v1/People"
                {:body body
                 :content-type content-type
                 :accept :xml})))

(defn update-person
  "Expects the id of a person. Updates a single person."
  [id & opts]
  (let [content-type (content-type-from-version (:version opts))]
    (api-action :PUT (str "/v1/People/" id) {:accept :xml :content-type content-type})))
