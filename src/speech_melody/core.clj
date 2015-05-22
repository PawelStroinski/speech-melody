(ns speech-melody.core
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [speech-melody.generator :refer [generate]]
            [speech-melody.uploader :refer [upload]])
  (:import [java.io File]))

(defn title [text lang]
  (str text " (" lang ")"))

(defn config []
  (-> "config.edn" io/resource io/file slurp edn/read-string))

;(println "Written to" (generate "Hello World" "en-us" :flac (title "Hello World" "en-us") "speech-melody"))

(def tmp (io/file "/var/folders/tg/q99chw2971v_rnzk9v6mpv100000gn/T/speech1026046135326911758.flac"))

(try
  (println "Uploaded to" (upload :soundcloud tmp (title "Hello World" "en-us") "speech-melody" (config)))
  (catch RuntimeException ex
    (println (str "Received error: " (.getMessage ex)))
    (when-let [ctx (ex-data ex)]
      (println (str "More information: " ctx)))))
