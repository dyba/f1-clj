(ns f1-clj.utils.http
  (:require [oauth.client :as oauth]
            [clj-http.client :as client]
            [f1-clj.core :refer :all]
            [f1-clj.utils.keyword :as k]))

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
