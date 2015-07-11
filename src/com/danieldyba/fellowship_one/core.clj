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

(defn content-type-from-version
  [version]
  (if (= (str version) (str "2"))
    "application/vnd.fellowshiponeapi.com.people.people.v2+xml"
    :xml))

(defn api-search
  [args]
  (println args)
  (let [normalized-params (reduce (fn [acc entry]
                                    (let [[k v] entry
                                          param (-> k k/keyword->str s/camel-case keyword)]
                                      (assoc acc param v)))
                                  {}
                                  (args :params))]
    (api-action :GET (args :uri) {:accept :xml :query-params normalized-params})))

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

(defn build-uri
  [uri params]
  (if-let [tokens (re-seq #"\:[A-Za-z\-]+" uri)]
    
    )
  )

(defmacro defendpoint
  "Defines an endpoint function"
  [nm & spec]
  `(defn ~nm
     [action# & opts#]
     (let [action->uri# ~(apply hash-map spec)
           http-method# (case action#
                          :new    :GET
                          :create :POST
                          :list   :GET
                          :update :PUT
                          :delete :DELETE
                          :show   :GET
                          :search :GET
                          :edit   :GET
                          (throw (Exception. (str "Unsupported action: " action#))))]
       (api-action http-method# (action->uri# action#) {:accept :xml}))))

(defendpoint households
  :new    "/v1/Households/new"
  :create "/v1/Households"
  :show   "/v1/Households/:household-id"
  :search "/v1/Households/search")

(defendpoint people
  :new    "/v1/People/new"
  :create "/v1/People"
  :show   "/v1/People/:person-id"
  :update "/v1/People/:person-id"
  :edit   "/v1/People/:person-id/edit"
  :search "/v1/People/search")

(defendpoint contribution-receipts
  :new    "/giving/v1/contributionreceipts/new"
  :search "/giving/v1/contributionreceipts/search"
  :create "/giving/v1/contributionreceipts"
  :show   "/giving/v1/contributionreceipts/:contribution-receipt-id")

(defendpoint contribution-types
  :list "/giving/v1/contributiontypes"
  :show "/giving/v1/contributiontypes/:contribution-type-id")

(defendpoint funds
  :list "/giving/v1/funds"
  :show "/giving/v1/funds/:fund-id")

(defendpoint fund-types
  :list "/giving/v1/funds/fundtypes"
  :show "/giving/v1/funds/fundtypes/:fund-type-id")

(defendpoint account-types
  :list "/giving/v1/accounts/accounttypes"
  :show "/giving/v1/accounts/accounttypes/:account-id")

(defendpoint household-member-types
  :list "/v1/People/HouseholdMemberTypes"
  :show "/v1/People/HouseholdMemberTypes/:household-member-type-id")

(defendpoint household-addresses
  :show   "/v1/Households/:household-id/Addresses/:address-id"
  :list   "/v1/Households/:household-id/Addresses"
  :edit   "/v1/Households/:household-id/Addresses/:address-id/edit"
  :new    "/v1/Households/:household-id/Addresses/new"
  :create "/v1/Households/:household-id/Addresses"
  :update "/v1/Households/:household-id/Addresses/:address-id"
  :delete "/v1/Households/:household-id/Addresses/:address-id")

(defendpoint people-addresses
  :show   "/v1/People/:person-id/Addresses/:address-id"
  :list   "/v1/People/:person-id/Addresses"
  :edit   "/v1/People/:person-id/Addresses/:address-id/edit"
  :new    "/v1/People/:person-id/Addresses/new"
  :create "/v1/People/:person-id/Addresses"
  :update "/v1/People/:person-id/Addresses/:address-id"
  :delete "/v1/People/:person-id/Addresses/:address-id")

(defendpoint people-attributes
  :show   "/v1/People/:person-id/Attributes/:attribute-id"
  :list   "/v1/People/:person-id/Attributes"
  :edit   "/v1/People/:person-id/Attributes/:attribute-id/edit"
  :new    "/v1/People/:person-id/Attributes/new"
  :create "/v1/People/:person-id/Attributes"
  :update "/v1/People/:person-id/Attributes/:attribute-id"
  :delete "/v1/People/:person-id/Attributes/:attribute-id")

(defmacro template
  "Creates an XML data structure from the `attrs` map."
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

  (household-member-types :list)
  (household-member-types :show 2)
  
  (people-addresses :new 12345)
  (people-addresses :create 12345 payload)
  (people-addresses :update {:person-id 12345 :address-id 7890} payload)
  (people-addresses :delete {:person-id 12345 :address-id 7890})

  (contribution-types :list)
  (contribution-types :show 12345)

  (funds :list)
  (funds :show 12345)
  
  (fund-types :list)
  (fund-types :show 12345)
  
  (account-types :list)
  (account-types :show 63976)
  
  (contribution-receipts :new)
  (contribution-receipts :create payload)
  (contribution-receipts :show 12345)
  (contribution-receipts :edit 12345)
  (contribution-receipts :edit {:contribution-receipt-id 12345})
  (contribution-receipts :search {:start-received-date "2015-12-12"})
  
  (template :households {:household-name "Nick and Rebecca Floyd"})
  (template :contribution-receipt {:fund "5778" :received-date "2008-08-25T00:00:00" :contribution-type "2"})

  (people-attributes :new {:persion-id 12345})
  (people-attributes :new 12345) 
  (people-attributes :list {:person-id 12345})
  (people-attributes :list 12345)
  (people-attributes :create {:person-id 12345} {:person (xe/person 12345)
                                                 :attribute-group (xe/attribute-group 123)})
  (people-attributes :edit {:person-id 12345 :attribute-id 17890})
  (people-attributes :update {:person-id 752 :attribute-id 221} payload)
  (people-attributes :show {:person-id 767 :attribute-id 890}))
