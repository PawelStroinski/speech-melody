(ns speech-melody.uploader
  (:require [robert.bruce :refer [try-try-again]])
  (:import [com.soundcloud.api ApiWrapper Request Endpoints Params$Track Http]
           [org.apache.http HttpStatus]))

(defmulti #^{:private true} try-upload (fn [provider & _] provider))

(defn- soundcloud-request [wrapper input title author tags description]
  (let [request (Request/to Endpoints/TRACKS nil)]
    (doto request
      (.add Params$Track/TITLE title)
      (.add Params$Track/LABEL_NAME author)
      (.add Params$Track/TAG_LIST tags)
      (.add Params$Track/DESCRIPTION description)
      (.add Params$Track/DOWNLOADABLE true)
      (.withFile Params$Track/ASSET_DATA input))
    (.post wrapper request)))

(defn- soundcloud-finished-processing [wrapper uri]
  (let [request (Request/to uri nil)
        state (-> (.get wrapper request) Http/getJSON (.get "state"))]
    (when (= state "processing")
      (println "Processing" uri)
      (throw (ex-info "Still processing" {:uri uri})))))

(defmethod try-upload :soundcloud [_ input title author config]
  (let [cfg (:soundcloud config)
        wrapper (ApiWrapper. (:client-id cfg) (:client-secret cfg) nil nil)
        _ (.login wrapper (:username cfg) (:password cfg) nil)
        tags "Blues Classical Electronic Speech"
        description "some description"
        response (soundcloud-request wrapper input title author tags description)
        status-code (.. response getStatusLine getStatusCode)
        reason (.. response getStatusLine getReasonPhrase)
        json (Http/getJSON response)]
    (when (not= status-code HttpStatus/SC_CREATED)
      (throw (ex-info "Unexpected status code"
                      {:status-code status-code, :reason reason, :args [input title], :json json})))
    (Thread/sleep 5000)
    (try-try-again {:sleep 5000 :tries 120} soundcloud-finished-processing wrapper (.get json "uri"))
    (.get json "permalink_url")))

(defn upload [& args]
  (try-try-again {:sleep 5000 :tries 120} apply try-upload args))
