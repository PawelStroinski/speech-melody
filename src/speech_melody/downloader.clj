(ns speech-melody.downloader
  (:require [clj-http.client :as client]
            [clojure.java.io :as io])
  (:import [java.io File]))

(defn- download-to [url output]
  (let [user-agent "Mozilla/5.0 (Windows NT 6.1;) Gecko/20100101 Firefox/13.0.1"
        request (client/get url {:as      :stream
                                 :headers {"User-Agent" user-agent}})]
    (with-open [input (:body request)]
      (io/copy input output))))

(defn- make-url [text lang]
  (str "http://translate.google.com/translate_tts?ie=UTF-8&"
       (client/generate-query-string {"q" text, "tl" lang})))

(defn download [{:keys [text lang]}]
  (let [temp-mp3 (File/createTempFile "speech" ".mp3")]
    (download-to (make-url text lang) temp-mp3)
    temp-mp3))
