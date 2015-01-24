(ns f1-clj.core
  (:require [clj-http.client :as client]
            [oauth.client :as oauth]
            [oauth.signature :as sig]
            [environ.core :refer [env]]
            [clojure.string :as str]
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
