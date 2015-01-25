(ns com.danieldyba.fellowship-one.contribution-receipts
  (:require [clj-http.client :as client]
            [clojure.zip :as zip]
            [clojure.data.xml :as xml]
            [clojure.data.zip.xml :as zx]
            [clojure.string :as str]
            [com.danieldyba.fellowship-one.utils.keyword :as k]
            [com.danieldyba.fellowship-one.utils.string :as s]
            [com.danieldyba.fellowship-one.utils.http :refer [api-action]]))

(defn new-receipt
  "Returns the template for a new receipt."
  []
  (api-action :GET "/giving/v1/contributionreceipts/new" {:accept :xml}))

(defn create-receipt
  "Expects an XML payload to send to the server."
  [body]
  (api-action :POST
              "/giving/v1/contributionreceipts"
              {:body body
               :content-type :xml
               :accept :xml}))

(defn show-receipt
  "Expects the id of a receipt. Returns the receipt with that id."
  [id]
  (api-action :GET (str "/giving/v1/contributionreceipts/" id) {:accept :xml}))

(defn search-receipts
  "Expects a map containing the request parameters with spear-cased keywords. Returns a list of contribution receipts."
  [params]
  (let [normalized-params (reduce (fn [acc entry]
                                    (let [[k v] entry
                                          param (-> k k/keyword->str s/camel-case keyword)]
                                      (assoc acc param v)))
                                  {}
                                  params)]
    (api-action :GET "/giving/v1/contributionreceipts/search" {:accept :xml :query-params normalized-params})))

(defn make-receipt-template
  "Expects a map containing keys that correspond to the elements of a contribution receipt template and values
   that are the Clojure representation of the XML data that will be substituted for those elements."
  [attrs]
  (let [template (-> (new-receipt) :body .getBytes java.io.ByteArrayInputStream. xml/parse)
        camel-case-keyword #(-> % k/keyword->str s/camel-case keyword)]
    (reduce (fn [acc entry]
              (-> (zip/xml-zip acc)
                  (zx/xml1-> (camel-case-keyword (key entry)))
                  (zip/replace (val entry))
                  (zip/root)))
            template
            attrs)))
