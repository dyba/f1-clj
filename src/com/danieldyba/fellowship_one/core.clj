(ns com.danieldyba.fellowship-one.core
  (:require [clj-http.client :as client]
            [oauth.client :as oauth]
            [oauth.signature :as sig]
            [environ.core :refer [env]]
            [clojure.string :as str]
            [clojure.data.zip.xml :as zx]
            [clojure.data.xml :as xml]
            [clojure.zip :as zip]
            [com.danieldyba.fellowship-one.utils.keyword :as k]
            [com.danieldyba.fellowship-one.utils.string :as s]
            [ring.util.codec :refer (base64-encode)]))

(def ^:dynamic church-code (env :f1-church-code))

(def ^:dynamic consumer-key (env :f1-consumer-key))

(def ^:dynamic consumer-secret (env :f1-consumer-secret))

(def ^:dynamic is-production (env :f1-production-mode))

(def ^:dynamic *oauth-consumer* nil)

(def ^:dynamic *oauth-token* nil)

(def ^:dynamic *oauth-token-secret* nil)

(defn base-url []
  (let [url-frag (if is-production
                   ""
                   ".staging")]
    (str "https://" church-code url-frag ".fellowshiponeapi.com")))

(defmacro with-oauth
  [consumer oauth-token oauth-token-secret & body]
  `(binding [*oauth-consumer* ~consumer
             *oauth-token* ~oauth-token
             *oauth-token-secret* ~oauth-token-secret]
     ~@body))

(def consumer
  (oauth/make-consumer consumer-key
                       consumer-secret
                       (str (base-url) "/v1/Tokens/RequestToken")
                       (str (base-url) "/v1/WeblinkUser/AccessToken")
                       (str (base-url) "/v1/WeblinkUser/Login")
                       :hmac-sha1))

;; You can also have validation functions that verify the xml content beforehand? Would this be useful???

(def unsigned-oauth-params ;; uses consumer
  (sig/oauth-params consumer
                    (sig/rand-str 30)
                    (sig/msecs->secs (System/currentTimeMillis))))

(defn oauth-access-request ;; uses consumer, unsigned-oauth-params
  [consumer uri unsigned-oauth-params & [extra-params]]
  (let [signature    (sig/sign consumer (sig/base-string "POST" uri (merge unsigned-oauth-params extra-params)) nil)
        oauth-params (assoc unsigned-oauth-params :oauth_signature signature)]
    (oauth/build-request oauth-params extra-params)))

(defn access-token ;; uses consumer, oauth-access-request, unsigned-oauth-params
  [username password]
  (let [base64-credentials (fn [username password]
                             (-> (str username " " password) .getBytes base64-encode String.))
        access-uri         (:access-uri consumer)]
    (oauth/post-request-body-decoded access-uri
                                     (oauth-access-request consumer
                                                           access-uri
                                                           unsigned-oauth-params
                                                           {:ec (base64-credentials username password)}))))

(defn api-action [method path & [opts]]
  (when (and *oauth-token* *oauth-token-secret* *oauth-consumer*)
    (let [url (str (base-url) path)
          query-params (:query-params opts)
          remaining-opts (dissoc opts :query-params)
          oauth-params (oauth/credentials *oauth-consumer*
                                          *oauth-token*
                                          *oauth-token-secret*
                                          (k/upper-case method)
                                          url
                                          query-params)]
      (client/request (merge {:method (k/lower-case method)
                              :url url
                              :query-params (into query-params oauth-params)}
                             remaining-opts)))))

(defn list-funds []
  (api-action :GET "/giving/v1/funds" {:accept :xml}))

(defn show-fund [id]
  (let [path (str "/giving/v1/funds/" id)]
    (api-action :GET path {:accept :xml})))

(defn list-contribution-types
  "Return a list of contribution types."
  []
  (api-action :GET "/giving/v1/contributiontypes" {:accept :xml}))

(defn show-contribution-type
  "Return a single contribution type for the given id."
  [id]
  (let [path (str "/giving/v1/contributiontypes/" id)]
    (api-action :GET path {:accept :xml})))

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

(defn list-account-types
  "Returns a list of account types."
  []
  (api-action :GET "/giving/v1/accounts/accounttypes" {:accept :xml}))

(defn show-account-type
  "Returns a single account type for the given id."
  [id]
  (let [path (str "/giving/v1/accounts/accounttypes/" id)]
    (api-action :GET path {:accept :xml})))

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

(defn update-batch
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

(defn search-households
  "Expects a map containing the request parameters with spear-cased keywords. Returns a collection of households
  for the parameters provided."
  [params & [opts]]
  (let [normalized-params (reduce (fn [acc entry]
                                    (let [[k v] entry
                                          param (-> k k/keyword->str s/camel-case keyword)]
                                      (assoc acc param v)))
                                  {}
                                  params)]
    (api-action :GET "/v1/Households/Search" (merge {:accept :xml :query-params normalized-params}))))

(defn show-household
  "Expects the id of a household. Returns a single household with that id."
  [id & opts]
  (api-action :GET (str "/v1/Households/" id) {:accept :xml}))

(defn edit-household
  "Expects the id of a household. Returns a single household with that id. Call this before updating
  a household in order to retrieve the resource in its most recent condition with its latest values."
  [id & opts]
  (api-action :GET (str "/v1/Households/" id "/Edit") {:accept :xml}))

(defn new-household
  "Returns the template for a new household."
  [& opts]
  (api-action :GET "/v1/Households/New" {:accept :xml}))

(defn create-household
  "Expects an XML payload to send to the server."
  [body & opts]
  (api-action :POST
              "/v1/Households"
              {:body body :accept :xml}))

(defn update-household
  "Expects the id of a household. Updates a single household."
  [id & opts]
  (api-action :PUT (str "/v1/Households/" id) {:accept :xml}))

(defn list-household-member-types
  "Return a list of contribution types."
  []
  (api-action :GET "/v1/People/HouseholdMemberTypes" {:accept :xml}))

(defn show-household-member-type
  "Return a single contribution type for the given id."
  [id]
  (let [path (str "/v1/People/HouseholdMemberTypes/" id)]
    (api-action :GET path {:accept :xml})))

(defn show-person-address
  "Expects the id of a person and the id of the address. Returns a single address for the
  person with person-id."
  [person-id address-id & opts]
  (api-action :GET
              (str "/v1/People/" person-id "/Addresses/" address-id)
              {:accept :xml}))

(defn list-person-addresss
  "Returns a list of addresss for a given person with person-id."
  [person-id & opts]
  (api-action :GET
              (str "/v1/People/" person-id "/Addresses" )
              {:accept :xml}))

(defn edit-person-address
  "Expects the id of a person and the address id. Call this before updating
  a person's address in order to retrieve the resource in its most recent
  condition with its latest values."
  [person-id address-id & opts]
  (api-action :GET
              (str "/v1/People/" person-id "/Addresses/" address-id "/Edit")
              {:accept :xml}))

(defn new-person-address
  "Returns the template for a new person address."
  [person-id & opts]
  (api-action :GET
              (str "/v1/People/" person-id "/Addresses/new")
              {:accept :xml}))

(defn create-person-address
  "Expects an XML payload to send to the server."
  [person-id body & opts]
  (api-action :POST
              (str "/v1/People/" person-id "/Addresses")
              {:body body
               :accept :xml}))

(defn update-person-address
  "Expects the id of a person. Updates a single person's address."
  [person-id address-id & opts]
  (api-action :PUT
              (str "/v1/People/" person-id "/Addresses/" address-id)
              {:accept :xml}))

(defn delete-person-address
  "Deletes the address with address-id for person with person-id."
  [person-id address-id & opts]
  (api-action :DELETE
              (str "/v1/People/" person-id "/Addresses/" address-id)
              {:accept :xml}))

(defn show-household-address
  "Expects the id of a household and the id of the address. Returns a single address for the
  household with household-id."
  [household-id address-id & opts]
  (api-action :GET
              (str "/v1/Households/" household-id "/Addresses/" address-id)
              {:accept :xml}))

(defn list-household-addresss
  "Returns a list of addresss for a given household with household-id."
  [household-id & opts]
  (api-action :GET
              (str "/v1/Households/" household-id "/Addresses" )
              {:accept :xml}))

(defn edit-household-address
  "Expects the id of a household and the address id. Call this before updating
  a household's address in order to retrieve the resource in its most recent
  condition with its latest values."
  [household-id address-id & opts]
  (api-action :GET
              (str "/v1/Households/" household-id "/Addresses/" address-id "/Edit")
              {:accept :xml}))

(defn new-household-address
  "Returns the template for a new household address."
  [household-id & opts]
  (api-action :GET
              (str "/v1/Households/" household-id "/Addresses/new")
              {:accept :xml}))

(defn create-household-address
  "Expects an XML payload to send to the server."
  [household-id body & opts]
  (api-action :POST
              (str "/v1/Households/" household-id "/Addresses")
              {:body body
               :accept :xml}))

(defn update-household-address
  "Expects the id of a household. Updates a single household's address."
  [household-id address-id & opts]
  (api-action :PUT
              (str "/v1/Households/" household-id "/Addresses/" address-id)
              {:accept :xml}))

(defn delete-household-address
  "Deletes the address with address-id for household with household-id."
  [household-id address-id & opts]
  (api-action :DELETE
              (str "/v1/Households/" household-id "/Addresses/" address-id)
              {:accept :xml}))
