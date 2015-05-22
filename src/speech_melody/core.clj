(ns speech-melody.core
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [speech-melody.generator :refer [generate]])
  (:import [java.io File]))

(defn title [text lang]
  (str text " (" lang ")"))

(println "Written to" (generate "Hello World" "en-us" :flac (title "Hello World" "en-us") "speech-melody"))