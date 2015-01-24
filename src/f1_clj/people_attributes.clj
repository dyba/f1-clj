(ns f1-clj.people-attributes
  (:require [clj-http.client :as client]
            [f1-clj.utils.keyword :as k]
            [f1-clj.utils.string :as s]
            [f1-clj.utils.http :refer [api-action]]))

(defn show-person-attribute
  "Expects the id of a person and the id of the attribute. Returns a single attribute for the
  person with person-id."
  [person-id attribute-id & opts]
  (api-action :GET
              (str "/v1/People/" person-id "/Attributes/" attribute-id)
              {:accept :xml}))

(defn list-person-attributes
  "Returns a list of attributes for a given person with person-id."
  [person-id & opts]
  (api-action :GET
              (str "/v1/People/" person-id "/Attributes" )
              {:accept :xml}))

(defn edit-person-attribute
  "Expects the id of a person and the attribute id. Call this before updating
  a person's attribute in order to retrieve the resource in its most recent
  condition with its latest values."
  [person-id attribute-id & opts]
  (api-action :GET
              (str "/v1/People/" person-id "/Attributes/" attribute-id "/Edit")
              {:accept :xml}))

(defn new-person-attribute
  "Returns the template for a new person attribute."
  [person-id & opts]
  (api-action :GET
              (str "/v1/People/" person-id "/Attributes/new")
              {:accept :xml}))

(defn create-person-attribute
  "Expects an XML payload to send to the server."
  [person-id body & opts]
  (api-action :POST
              (str "/v1/People/" person-id "/Attributes")
              {:body body
               :accept :xml}))

(defn update-person-attribute
  "Expects the id of a person. Updates a single person's attribute."
  [person-id attribute-id & opts]
  (api-action :PUT
              (str "/v1/People/" person-id "/Attributes/" attribute-id)
              {:accept :xml}))

(defn delete-person-attribute
  "Deletes the attribute with attribute-id for person with person-id."
  [person-id attribute-id & opts]
  (api-action :DELETE
              (str "/v1/People/" person-id "/Attributes/" attribute-id)
              {:accept :xml}))
