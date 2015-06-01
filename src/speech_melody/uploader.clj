(ns speech-melody.uploader
  (:require [robert.bruce :refer [try-try-again]])
  (:import [com.soundcloud.api ApiWrapper Request Endpoints Params$Track Http]
           [org.apache.http HttpStatus]))

(defmulti upload (fn [_ _ {:keys [upload-to]}] upload-to))

(defn- post-request-to-soundcloud [input {:keys [title author tags description]} wrapper]
  (let [request (Request/to Endpoints/TRACKS nil)]
    (doto request
      (.add Params$Track/TITLE title)
      (.add Params$Track/LABEL_NAME author)
      (.add Params$Track/TAG_LIST tags)
      (.add Params$Track/DESCRIPTION description)
      (.add Params$Track/DOWNLOADABLE true)
      (.withFile Params$Track/ASSET_DATA input))
    (.post wrapper request)))

(defn- upload-to-soundcloud [input metadata cfg wrapper]
  (.login wrapper (:username cfg) (:password cfg) nil)
  (let [response (post-request-to-soundcloud input metadata wrapper)
        status-code (.. response getStatusLine getStatusCode)
        reason (.. response getStatusLine getReasonPhrase)
        json (Http/getJSON response)]
    (when (not= status-code HttpStatus/SC_CREATED)
      (throw (ex-info "Unexpected status code"
                      {:status-code status-code, :reason reason, :args [input metadata], :json json})))
    json))

(defn- ensure-soundcloud-has-finished-processing [uri wrapper]
  (let [request (Request/to uri nil)
        state (-> (.get wrapper request) Http/getJSON (.get "state"))]
    (when (= state "processing")
      (println "Processing" uri)
      (throw (ex-info "Still processing" {:uri uri})))))

(defmethod upload :soundcloud [input metadata config]
  (let [cfg (:soundcloud config)
        wrapper (ApiWrapper. (:client-id cfg) (:client-secret cfg) nil nil)
        try-options {:sleep 1000, :decay :double, :tries 15} ; about 136 mins max and 273 mins total
        json (try-try-again try-options upload-to-soundcloud input metadata cfg wrapper)]
    (Thread/sleep 5000)
    (try-try-again try-options ensure-soundcloud-has-finished-processing (.get json "uri") wrapper)
    (.get json "permalink_url")))
