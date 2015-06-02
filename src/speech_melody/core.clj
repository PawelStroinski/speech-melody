(ns speech-melody.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [speech-melody.generator :refer [generate]]
            [speech-melody.uploader :refer [upload]]
            [speech-melody.downloader :refer [download]]
            [speech-melody.language :refer [text-lang]]))

(defn read-config []
  (-> "config.edn" io/resource io/file slurp edn/read-string))

(defn make-metadata [{:keys [text user]} {:keys [metadata] :as config}]
  (let [format-map (fn [m k arg] (assoc m k (format (k m) arg)))
        textlang (text-lang text config)]
    (-> metadata
        (merge textlang)
        (format-map :title text)
        (format-map :author user)
        (format-map :description (:lang textlang)))))

(let [config (read-config)
      metadata (make-metadata {:text "Hello World", :user "Paweł"} config)
      temp-mp3 (download metadata config)
      temp-out (generate temp-mp3 metadata)
      url (upload temp-out metadata config)]
  (println "Downloaded to" temp-mp3)
  (println "Written to" temp-out)
  (println "Uploaded to" url)
  (.delete temp-mp3)
  (.delete temp-out)
  )
