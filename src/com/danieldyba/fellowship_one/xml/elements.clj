(ns com.danieldyba.fellowship-one.xml.elements
  (:require [clj-time.format :as f]
            [clj-time.core :as t]
            [clojure.xml :as xml]
            [clojure.data.zip.xml :as zx]
            [com.danieldyba.fellowship-one.contribution-types :refer [show-type]]
            [com.danieldyba.fellowship-one.funds :refer [show-fund]]
            [clojure.zip :as zip]))

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
  (zip/node (xml-root (show-type id))))

(defn fund
  [id]
  (let [zipper (xml-root (show-fund id))
        uri (zx/attr (zx/xml1-> zipper) :uri)
        id (zx/attr (zx/xml1-> zipper) :id)
        content (zip/node (zx/xml1-> zipper :name))]
    {:tag :fund
     :attrs {:uri uri :id id}
     :content [content]}))
