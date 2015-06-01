(ns speech-melody.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [speech-melody.generator :refer [generate]]
            [speech-melody.uploader :refer [upload]]
            [speech-melody.downloader :refer [download]]))

(defn title [text lang]
  (str text " (" lang ")"))

(defn read-config []
  (-> "config.edn" io/resource io/file slurp edn/read-string))

(defn make-metadata [text lang config]
  (let [metadata (:metadata config)]
    (merge metadata {:text  text
                     :lang  lang
                     :title (title text lang)
                     })))

(let [config (read-config)
      metadata (make-metadata "Hello World" "en-us" config)
      temp-mp3 (download metadata)
      temp-out (generate temp-mp3 metadata)
      url (upload temp-out metadata config)]
  (println "Downloaded to" temp-mp3)
  (println "Written to" temp-out)
  (println "Uploaded to" url)
  (.delete temp-mp3)
  (.delete temp-out)
  )
