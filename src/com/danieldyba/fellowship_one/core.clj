(ns com.danieldyba.fellowship-one.core
  (:require [clj-http.client :as client]
            [oauth.client :as oauth]
            [oauth.signature :as sig]
            [environ.core :refer [env]]
            [ring.util.codec :refer (base64-encode)]
            [clojure.data.xml :as data-xml]
            [clojure.xml :as xml]
            [clojure.data.zip.xml :as zx]
            [clojure.zip :as zip]
            [clojure.string :as str]
            [clj-time.format :as f]
            [clj-time.core :as t]
            [com.danieldyba.fellowship-one.utils.keyword :as k]
            [com.danieldyba.fellowship-one.utils.string :as s]))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Households API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-household
  "Expects an XML payload to send to the server."
  [body]
  (api-action :POST
              "/v1/Households"
              {:body body
               :content-type :xml
               :accept :xml}))

(defn show-household
  "Expects the id of a household. Returns the household with that id."
  [id]
  (api-action :GET (str "/v1/Households/" id) {:accept :xml}))

(defn search-households
  "Expects a map containing the request parameters with spear-cased keywords. Returns a list of households."
  [params]
  (let [normalized-params (reduce (fn [acc entry]
                                    (let [[k v] entry
                                          param (-> k k/keyword->str s/camel-case keyword)]
                                      (assoc acc param v)))
                                  {}
                                  params)]
    (api-action :GET "/v1/Households/Search" {:accept :xml :query-params normalized-params})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; People API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; People Attributes API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; People Addresses API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn show-person-address
  "Expects the id of a person and the id of the attribute. Returns a single attribute for the
  person with person-id."
  [person-id address-id & opts]
  (api-action :GET
              (str "/v1/People/" person-id "/Addresses/" address-id)
              {:accept :xml}))

(defn list-person-address
  "Returns a list of attributes for a given person with person-id."
  [person-id & opts]
  (api-action :GET
              (str "/v1/People/" person-id "/Addresses" )
              {:accept :xml}))

(defn edit-person-address
  "Expects the id of a person and the attribute id. Call this before updating
  a person's attribute in order to retrieve the resource in its most recent
  condition with its latest values."
  [person-id address-id & opts]
  (api-action :GET
              (str "/v1/People/" person-id "/Addresses/" address-id "/edit")
              {:accept :xml}))

(defn new-person-address
  "Returns the template for a new person attribute."
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
  "Expects the id of a person. Updates a single person's attribute."
  [person-id address-id & opts]
  (api-action :PUT
              (str "/v1/People/" person-id "/Addresses/" address-id)
              {:accept :xml}))

(defn delete-person-address
  "Deletes the attribute with attribute-id for person with person-id."
  [person-id address-id & opts]
  (api-action :DELETE
              (str "/v1/People/" person-id "/Addresses/" address-id)
              {:accept :xml}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Household Addresses API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn show-household-address
  "Expects the id of a household and the id of the attribute. Returns a single attribute for the
  household with household-id."
  [household-id address-id & opts]
  (api-action :GET
              (str "/v1/Households/" household-id "/Addresses/" address-id)
              {:accept :xml}))

(defn list-household-address
  "Returns a list of attributes for a given household with household-id."
  [household-id & opts]
  (api-action :GET
              (str "/v1/Households/" household-id "/Addresses" )
              {:accept :xml}))

(defn edit-household-address
  "Expects the id of a household and the attribute id. Call this before updating
  a household's attribute in order to retrieve the resource in its most recent
  condition with its latest values."
  [household-id address-id & opts]
  (api-action :GET
              (str "/v1/Households/" household-id "/Addresses/" address-id "/edit")
              {:accept :xml}))

(defn new-household-address
  "Returns the template for a new household attribute."
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
  "Expects the id of a household. Updates a single household's attribute."
  [household-id address-id & opts]
  (api-action :PUT
              (str "/v1/Households/" household-id "/Addresses/" address-id)
              {:accept :xml}))

(defn delete-household-address
  "Deletes the attribute with attribute-id for household with household-id."
  [household-id address-id & opts]
  (api-action :DELETE
              (str "/v1/Households/" household-id "/Addresses/" address-id)
              {:accept :xml}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Contribution Receipts API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-contribution-receipt
  "Expects an XML payload to send to the server."
  [body]
  (api-action :POST
              "/giving/v1/contributionreceipts"
              {:body body
               :content-type :xml
               :accept :xml}))

(defn show-contribution-receipt
  "Expects the id of a receipt. Returns the receipt with that id."
  [id]
  (api-action :GET (str "/giving/v1/contributionreceipts/" id) {:accept :xml}))

(defn search-contribution-receipts
  "Expects a map containing the request parameters with spear-cased keywords. Returns a list of contribution receipts."
  [params]
  (let [normalized-params (reduce (fn [acc entry]
                                    (let [[k v] entry
                                          param (-> k k/keyword->str s/camel-case keyword)]
                                      (assoc acc param v)))
                                  {}
                                  params)]
    (api-action :GET "/giving/v1/contributionreceipts/search" {:accept :xml :query-params normalized-params})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Experimental
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn api-search [args]
  (let [normalized-params (reduce (fn [acc entry]
                                    (let [[k v] entry
                                          param (-> k k/keyword->str s/camel-case keyword)]
                                      (assoc acc param v)))
                                  {}
                                  (args :params))]
    (api-action :GET (args :uri) {:accept :xml :query-params normalized-params})))

(defn- compile-create [action opts]
  (if (number? (first opts))
    (api-call (first opts) (fnext opts) (last opts))
    (api-call (first opts) (fnext opts))))

(defn compile-uri
  "Replaces :id components in the URI with ids.
  
  When there is only one :id component, ids should be a numerical value.
  When there are more than one :id components, the ids should be a map."
  [uri & [ids]]
  (if-let [tokens (re-seq #"\:[A-Za-z\-]+" uri)]
    (cond
      (= 0 (count tokens)) uri
      (= 1 (count tokens)) (str/replace uri (re-pattern (first tokens)) (str ids))
      :else (reduce (fn [acc [k v]]
                      (str/replace acc (re-pattern (str k)) (str v))) uri ids))
    uri))

(defn- select-request
  "Matches the action to the corresponding endpoint and makes the API call.
  Used as a helper for the macro defendpoint."
  [action uri & opts]
  `(let [action# ~action
         uri#    ~uri]
     (case action#
       :new    (api-action :GET uri# {:accept :xml})
       :delete (api-action :DELETE uri#)
       :list   (api-action :GET uri# {:accept :xml})
       :show   (api-action :GET uri# {:accept :xml})
       :edit   (api-action :GET uri# {:accept :xml})
       :search (api-action :GET uri# {:accept :xml})
       :update (api-action :PUT uri# {:accept :xml})
       :create (api-action :POST uri# {:accept :xml}))))

(defn- action-uri-clauses
  "Accepts a map where the keys are the actions and the values are the uris
  associated with the corresponding key.
  Used as a helper for the macro defendpoint."
  [action-uri-map & opts]
  (let [actions (keys action-uri-map)
        uris (vals action-uri-map)]
    (interleave actions (map (fn [[action uri]]
                               (select-request action uri)) action-uri-map))))

(defmacro defendpoint
  [nm & action-uris]
  (let [action-uri-map (reduce (fn [acc [k v]]
                                 (assoc acc k v)) {} (partition-all 2 action-uris))]
    `(defn ~nm
       [action# & opts#]
       (let [action-uri-map# ~action-uri-map]
         (case action#
           ~@(action-uri-clauses action-uri-map `opts#)
           (str "The " action# " action is not supported."))))))

#_(comment :list (api-call {:action action# :uri (compile-uri (action-uri-map# action#)) :opts opts#})
         :new (api-call {:action action# :uri (action-uri-map# action#) :opts opts#})
         :delete (api-call {:action action# :uri (compile-uri (action-uri-map# action#) (first opts#))})
         :show (api-call {:action action# :uri (compile-uri (action-uri-map# action#) (first opts#))})
         :search (api-call {:action action# :uri (action-uri-map# action#) :params (first opts#)})
         :create (compile-create action# opts#))

(defendpoint households
  :new "/v1/Households/new"
  :create "/v1/Households"
  :show "/v1/Households/:household-id"
  :search "/v1/Households/search")

(defendpoint people
  :new "/v1/People/new"
  :create "/v1/People"
  :show "/v1/People/:person-id"
  :update "/v1/People/:person-id"
  :edit "/v1/People/:person-id/edit"
  :search "/v1/People/search")

(defendpoint contribution-receipts
  :new "/giving/v1/contributionreceipts/new"
  :search "/giving/v1/contributionreceipts/search"
  :create "/giving/v1/contributionreceipts"
  :show "/giving/v1/contribution-receipts/:contribution-receipt-id")

(defendpoint contribution-types
  :list "/giving/v1/contributiontypes"
  :show "/giving/v1/contributiontypes/:contribution-type-id")

(defendpoint funds
  :list "/giving/v1/funds"
  :show "/giving/v1/funds/:fund-id")

(defendpoint account-types
  :list "/giving/v1/accounts/accounttypes"
  :show "/giving/v1/accounts/accounttypes/:account-id")

(defendpoint household-member-types
  :list "/v1/People/HouseholdMemberTypes"
  :show "/v1/People/HouseholdMemberTypes/:household-member-type-id")

(defendpoint household-addresses
  :show "/v1/Households/:household-id/Addresses/:address-id"
  :list "/v1/Households/:household-id/Addresses"
  :edit "/v1/Households/:household-id/Addresses/:address-id/edit"
  :new "/v1/Households/:household-id/Addresses/new"
  :create "/v1/Households/:household-id/Addresses"
  :update "/v1/Households/:household-id/Addresses/:address-id"
  :delete "/v1/Households/:household-id/Addresses/:address-id")

(defendpoint people-addresses
  :show "/v1/People/:person-id/Addresses/:address-id"
  :list "/v1/People/:person-id/Addresses"
  :edit "/v1/People/:person-id/Addresses/:address-id/edit"
  :new "/v1/People/:person-id/Addresses/new"
  :create "/v1/People/:person-id/Addresses"
  :update "/v1/People/:person-id/Addresses/:address-id"
  :delete "/v1/People/:person-id/Addresses/:address-id")

(defendpoint people-attributes
  :show "/v1/People/:person-id/Attributes/:attribute-id"
  :list "/v1/People/:person-id/Attributes"
  :edit "/v1/People/:person-id/Attributes/:attribute-id/edit"
  :new "/v1/People/:person-id/Attributes/new"
  :create "/v1/People/:person-id/Attributes"
  :update "/v1/People/:person-id/Attributes/:attribute-id"
  :delete "/v1/People/:person-id/Attributes/:attribute-id")

(defmacro template
  [endpoint attrs]
  `(let [template# (-> (~(k/keyword->sym endpoint) :new) :body .getBytes java.io.ByteArrayInputStream. data-xml/parse)
         camel-case-keyword# #(-> % k/keyword->str s/camel-case keyword)]
     (reduce (fn [acc# entry#]
               (-> (zip/xml-zip acc#)
                   (zx/xml1-> (camel-case-keyword# (key entry#)))
                   (zip/replace (val entry#))
                   (zip/root)))
             template#
             ~attrs)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; XML Helper functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn to-xml
  [node]
  (with-out-str (xml/emit-element node)))

(defn xml-root
  "Retrieves the xml data in the body of a response and converts it to a zipper data structure. If the body is empty, returns nil."
  [response]
  (let [body (:body response)]
    (if (not-empty body)
      (-> body
          .getBytes
          java.io.ByteArrayInputStream.
          xml/parse
          zip/xml-zip))))

(defn amount [amt]
  {:tag :amount :content [(str amt)]})

(defn received-date [date]
  (let [parser        #(f/parse (f/formatters :date-hour-minute-second) %)
        unparser      #(f/unparse (f/formatters :date-hour-minute-second) %)
        received-date (unparser (parser date))]
    {:tag :receivedDate
     :content [received-date]}))

(defn contribution-type
  [id]
  (zip/node (xml-root (contribution-types :show id))))

(defn fund
  [id]
  (let [zipper (xml-root (funds :show id)) ;; we don't yet support the show action :/
        uri (zx/attr (zx/xml1-> zipper) :uri)
        id (zx/attr (zx/xml1-> zipper) :id)
        content (zip/node (zx/xml1-> zipper :name))]
    {:tag :fund
     :attrs {:uri uri :id id}
     :content [content]}))


(defmacro let-uri
  [[bindings [uri ids]] & body]
  ;; take "/v1/Households/:household-id/Addresses
  ;; and convert from:
  (let [tokens (map (fn [match]
                      (-> match k/keyword->str symbol))
                    (re-seq #"\:[A-Za-z]+" uri))]
    #_(vec (flatten (vec (zipmap (map (fn [match] (-> match k/keyword->str symbol)) (re-seq #"\:[A-Za-z\-]+" "/v1/Households/:household-id/Addresses/:address-id")) [1234 7890]))))
    )
  `(let []
     (str )
     )
  (comment
    (let-uri [args [uri id-or-map]]
             (api-action :POST args {:content-type :xml :accept :xml :body payload})))
  ;; to...
  (comment
    (let [household-id 12345]
      (api-action :POST (str "/v1/Households/" household-id "/Addresses") {:content-type :xml :accept :xml :body payload}))))

(comment ;; our wishful thinking API
  (households :new)
  (households :show 76532)
  (households :search {:search-for "Turner" :page 3})
  (households :create payload)

  (people-addresses :new)
  (people-addresses :create 12345 payload)
  (people-addresses :update {:person-id 12345 :address-id 7890} payload)
  (people-addresses :delete 12345)

  (contribution-types :list)
  (contribution-types :show 12345)

  (funds :list)
  (funds :show 12345)
  
  (account-types :list)
  (account-types :show 63976)
  
  (contribution-receipts :new)
  (contribution-receipts :create payload)
  (contribution-receipts :show 12345)
  (contribution-receipts :search {:start-received-date "2015-12-12"})
  
  (template :households {:household-name "Nick and Rebecca Floyd"})
  (template :contribution-receipt {:fund "5778" :received-date "2008-08-25T00:00:00" :contribution-type "2"})

  (people-attributes :update {:person-id 752 :attribute-id 221} payload)
  (people-attributes :show {:person-id 767 :attribute-id 890}))
