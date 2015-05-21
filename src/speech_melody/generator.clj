(ns speech-melody.generator
  (:require [clj-http.client :as client]
            [clojure.java.io :as io])
  (:import [javax.sound.sampled AudioSystem]
           [java.io File]
           [comirva.audio.util MFCC AudioPreProcessor]
           [speech_melody.java VorbisEncoder]))

(def use-external-server (= (System/getProperty "os.name") "Windows 7"))

(if use-external-server
  (do
    (require '[overtone.core :refer :all])
    (eval '(defonce boot (overtone.sc.server/boot-external-server))))
  (require '[overtone.live :refer :all]))
(require '[leipzig.live :as live]
         '[leipzig.scale :as scale]
         '[leipzig.melody :refer [bpm is phrase then times where with tempo all]]
         '[overtone.inst.sampled-piano :refer :all])

(defn- download [url output]
  (let [user-agent "Mozilla/5.0 (Windows NT 6.1;) Gecko/20100101 Firefox/13.0.1"
        request (client/get url {:as      :stream
                                 :headers {"User-Agent" user-agent}})]
    (with-open [input (:body request)]
      (io/copy input output))))

(defn- make-url [text lang]
  (str "http://translate.google.com/translate_tts?ie=UTF-8&"
       (client/generate-query-string {"q" text, "tl" lang})))

(defn- audio->mfccs [input number-of-coefficients]
  (let [ais (AudioSystem/getAudioInputStream input)
        audio-sample-rate (.. ais getFormat getSampleRate)
        pre (AudioPreProcessor. ais audio-sample-rate)
        mfcc-gen (MFCC. audio-sample-rate 128 (inc number-of-coefficients) false)]
    (map seq (.process mfcc-gen pre))))

(def NUMBER-OF-COEFFICIENTS 3)

(defn- preprocess-mfccs [input]
  (let [drop-zero (partial drop-while (partial every? zero?))]
    (->> input drop-zero reverse drop-zero reverse)))

(defn- melody [mfccs]
  (flatten
    (for [i (range NUMBER-OF-COEFFICIENTS)]
      (map-indexed (fn [idx itm] (-> {:pitch (* i 4), :time (/ idx 16), :duration (/ itm 500)}))
                   (map #(nth % i) mfccs)))))

(def PIANO-GROUP 5)

(defn- piano [mfccs]
  (let [averages (fn [coll] (map #(/ (apply + %) PIANO-GROUP) (partition PIANO-GROUP coll)))]
    (->> (phrase (map #(/ (Math/abs %) 50) (averages (map last mfccs)))
                 (averages (map second mfccs)))
         (all :part :piano))))

(definst beep [frequency 440 volume 1.0]
         (-> frequency
             saw
             (* (env-gen (lin 0.1 0.1 0.2 volume) :action FREE))))

(defmethod live/play-note :default [{midi :pitch volume :duration}]
  (-> midi midi->hz (beep volume)))

(defmethod live/play-note :piano [{midi :pitch}]
  (sampled-piano midi))

(defn- play [mfccs]
  (->>
    (sort-by :time (concat (piano mfccs) (melody mfccs)))
    (tempo (bpm 40))
    (where :pitch (comp scale/D scale/blues))
    live/play))

(defn- drop-seconds! [input-ais seconds]
  (let [format (.getFormat input-ais)
        frame-size (.getFrameSize format)
        frame-rate (int (.getFrameRate format))
        bytes-per-second (* frame-size frame-rate)
        drop-bytes (* seconds bytes-per-second)]
    (.skip input-ais drop-bytes)))

(defn- postprocess-audio [input output title]
  (with-open [input-ais (AudioSystem/getAudioInputStream input)]
    (let [frame-rate (int (.. input-ais getFormat getFrameRate))]
      (drop-seconds! input-ais 1.45)
      (VorbisEncoder/encode input-ais output frame-rate title "speech-melody"))))

(defn generate [text lang]
  (let [temp-mp3 (File/createTempFile "speech" ".mp3")
        temp-wav (File/createTempFile "speech" ".wav")
        temp-ogg (File/createTempFile "speech" ".ogg")
        _ (download (make-url text lang) temp-mp3)
        mfccs (preprocess-mfccs (audio->mfccs temp-mp3 NUMBER-OF-COEFFICIENTS))]
    (recording-start temp-wav)
    @(play mfccs)
    (Thread/sleep (* 5 1000))
    (recording-stop)
    (postprocess-audio temp-wav temp-ogg (str text " (" lang ")"))
    (.delete temp-mp3)
    (.delete temp-wav)
    temp-ogg))
