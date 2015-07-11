# fellowship-one

Fellowship One API client for Clojure

[![Clojars Project](http://clojars.org/com.danieldyba/fellowship-one/latest-version.svg)](http://clojars.org/com.danieldyba/fellowship-one)

## Getting Started

Download and install [Leiningen](http://leiningen.org/). On a mac, use [Homebrew](http://brew.sh/) to download and install Leiningen.

I recommend that you use an editor that supports checking for closed parentheses. Emacs and the [Paredit](http://emacswiki.org/emacs/ParEdit) plugin are an excellent combination. You'll be making heavy use of the [Paredit Reference Card](http://pub.gajendra.net/src/paredit-refcard.pdf).

Check out the documentation on FellowshipOne's website to see how to use the API: [http://developer.fellowshipone.com/docs/](http://developer.fellowshipone.com/docs/). You'll need to register for an account if you want to post on the forums.

## Usage

You'll need to store your environment variables using the ```lein-environ``` plugin:

``` clojure
;; In ~/.lein/profiles.clj
{:user
 {:plugins [[lein-environ "1.0.0"]]}}
```

Then in create a ```profiles.clj``` file in the root of your project:

``` clojure
;; in profiles.clj in the root of your app
{:dev
 {:env
  {:f1-church-code "your-church-code"
   :f1-consumer-key "XXX"
   :f1-consumer-secret "m0r3-s3cr37s"
   :f1-production-mode false}}} ;; false => staging; true => production
```

Start up a REPL session. Here's a sample usage of the code:

``` clojure
    (ns f1-testdrive.core
      (:require [com.danieldyba.fellowship-one.core :refer :all]
                [com.danieldyba.fellowship-one.xml.elements :as xe]))
                
    (def consumer ...)
    (def oauth-token ...)
    (def oauth-token-secret ...)
    
    (with-oauth consumer
                oauth-token
                oauth-token-secret
      (new-receipt)
      (list-contribution-types)
      (search-receipts {:start-received-date "12-25-2014"}))
    (with-oauth consumer
                oauth-token
                oauth-token-secret
      (let [amount (xe/amount "15.00")
            received-date (xe/received-date "2014-12-31T12:00:00")
            contribution-type (xe/contribution-type 2)
            fund (xe/fund 220811)
            receipt-payload (make-receipt-template
                              {:amount amount
                               :contribution-type contribution-type
                               :fund fund
                               :received-date received-date})]
        (create-receipt (xe/to-xml receipt-payload))))
```

The FellowshipOne servers provide XML and JSON responses albeit the JSON responses are non-standard and require a JSON parser that preserves the order of the keys. This is why we've chosen to only support parsing responses containing XML data.

## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
