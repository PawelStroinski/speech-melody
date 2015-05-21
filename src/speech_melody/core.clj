(ns speech-melody.core
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [speech-melody.generator :refer [generate]])
  (:import [java.io File]))

(println "Written to" (generate "Hello World" "en-us" :flac))