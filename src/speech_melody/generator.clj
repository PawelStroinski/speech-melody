(ns speech-melody.generator
  (:require [clj-http.client :as client]
            [clojure.java.io :as io])
  (:import [javax.sound.sampled AudioSystem]
           [java.io File]
           [comirva.audio.util MFCC AudioPreProcessor]
           [speech_melody.java VorbisEncoder]
           [javaFlacEncoder FLACFileWriter]))

(def #^{:private true} use-external-server (= (System/getProperty "os.name") "Windows 7"))

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

(defn- preprocess-mfccs [input]
  (let [drop-zero (partial drop-while (partial every? zero?))]
    (->> input drop-zero reverse drop-zero reverse)))

(defn- melody [mfccs number-of-coefficients]
  (flatten
    (for [i (range number-of-coefficients)]
      (map-indexed (fn [idx itm] (-> {:pitch (* i 4), :time (/ idx 16), :duration (/ itm 500)}))
                   (map #(nth % i) mfccs)))))

(defn- piano [mfccs piano-group]
  (let [averages (fn [coll] (map #(/ (apply + %) piano-group) (partition piano-group coll)))]
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

(defn- play [mfccs number-of-coefficients piano-group]
  (->>
    (sort-by :time (concat (piano mfccs piano-group) (melody mfccs number-of-coefficients)))
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

(defmulti #^{:private true} encode (fn [format & _] format))
(defmethod encode :ogg [_ input-ais output title author]
  (let [frame-rate (int (.. input-ais getFormat getFrameRate))]
    (VorbisEncoder/encode input-ais output frame-rate title author)))
(defmethod encode :flac [_ input-ais output _ _]
  (AudioSystem/write input-ais FLACFileWriter/FLAC output))

(defn- postprocess-audio [input output format title author]
  (with-open [input-ais (AudioSystem/getAudioInputStream input)]
    (drop-seconds! input-ais 1.45)
    (encode format input-ais output title author)))

(defn generate [text lang format title author]
  (let [number-of-coefficients 3
        piano-group 5
        temp-mp3 (File/createTempFile "speech" ".mp3")
        temp-wav (File/createTempFile "speech" ".wav")
        temp-out (File/createTempFile "speech" (str "." (name format)))
        _ (download (make-url text lang) temp-mp3)
        mfccs (preprocess-mfccs (audio->mfccs temp-mp3 number-of-coefficients))]
    (recording-start temp-wav)
    @(play mfccs number-of-coefficients piano-group)
    (Thread/sleep (* 5 1000))
    (recording-stop)
    (postprocess-audio temp-wav temp-out format title author)
    (.delete temp-mp3)
    (.delete temp-wav)
    temp-out))
