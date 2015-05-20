(ns speech-melody.generator
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [overtone.studio.scope :refer [scope pscope spectrogram bus-freqs->buf]]
            [incanter.core :refer [view]]
            [incanter.charts :refer [xy-plot function-plot add-function]])
  (:import [javax.sound.sampled AudioSystem AudioFormat AudioFormat$Encoding AudioFileFormat$Type AudioInputStream]
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

(comment
  (defn- audio->wav [input output]
    "Based on http://stackoverflow.com/a/14144956"
    (let [convert-format (fn [source-ais] (let [source-format (.getFormat source-ais)
                                                sample-rate (.getSampleRate source-format)
                                                channels (.getChannels source-format)]
                                            (AudioFormat. AudioFormat$Encoding/PCM_SIGNED
                                                          sample-rate 16 channels
                                                          (* 2 channels) sample-rate false)))]
      (with-open [source-ais (AudioSystem/getAudioInputStream input)
                  convert-ais (AudioSystem/getAudioInputStream (convert-format source-ais)
                                                               source-ais)]
        (AudioSystem/write convert-ais AudioFileFormat$Type/WAVE output))))
  )

(defn- make-url [text lang]
  (str "http://translate.google.com/translate_tts?ie=UTF-8&"
       (client/generate-query-string {"q" text, "tl" lang})))

;(comment
; (audio->wav temp-mp3 temp-wav)
;  )
;(if windows
;  (def temp-wav (io/file "C:\\Users\\Pawel.Stroinski\\AppData\\Local\\Temp\\speech531472945742561508.wav"))
;  (def temp-wav (io/file "/var/folders/tg/q99chw2971v_rnzk9v6mpv100000gn/T/speech7680402333751887892.wav")))

(comment
  (def speech (load-sample (.getAbsolutePath temp-wav)))
  (def speech-rate (buf-rate-scale speech))
  )

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

(comment
  (view (xy-plot (range (count mfccs)) (map (partial apply +) mfccs)))
  (println (apply min (map (partial apply min) mfccs))
           (apply max (map (partial apply max) mfccs)))
  (defn- plot
    ([from]
     (plot from 1))
    ([from cnt]
     (let [to (+ from (dec cnt))
           len (dec (count mfccs))
           fp (function-plot #(nth (nth mfccs %) from) 0 len
                             :legend true :title (str from "-" to))]
       (doseq [i (range (inc from) (inc to))]
         (add-function fp #(nth (nth mfccs %) i) 0 len))
       (view fp))))
  (def PLOT-COUNT 1)
  (def plot-step (quot NUMBER-OF-COEFFICIENTS PLOT-COUNT))
  (doseq [i (range PLOT-COUNT)]
    (let [add (if (= i (dec PLOT-COUNT))
                (rem NUMBER-OF-COEFFICIENTS PLOT-COUNT)
                0)]
      (plot (* i plot-step) (+ plot-step add))))
  )

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

(comment
  (defn- speak []
    [{:time (/ ((comp :time last melody)) 2), :duration 1, :part :speak}])
  )

(definst beep [frequency 440 volume 1.0]
         (-> frequency
             saw
             (* (env-gen (lin 0.1 0.1 0.2 volume) :action FREE))))

(comment
  (definst speaker []
           (let [src (play-buf 1 speech speech-rate)
                 freq (concat (range 900 0 -100) (range 50 0 -5))
                 mul (range 0.05 1.0 0.05)
                 osc (map #(* src (sin-osc %1) %2) freq mul)
                 synth (+ (apply + osc) (* 10 (bpf src 100)))]
             [synth synth]))
  )

(defmethod live/play-note :default [{midi :pitch volume :duration}]
  (-> midi midi->hz (beep volume)))

(defmethod live/play-note :piano [{midi :pitch}]
  (sampled-piano midi))

(comment
  (defmethod live/play-note :speak [{}]
    (speaker))
  )

(comment (live/stop))

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
    @(->>
       ; (sort-by :time (concat (piano) (melody) (speak)))
       (sort-by :time (concat (piano mfccs) (melody mfccs)))
       (tempo (bpm 40))
       (where :pitch (comp scale/D scale/blues))
       live/play)
    (Thread/sleep (* 5 1000))
    (recording-stop)
    (postprocess-audio temp-wav temp-ogg (str text " (" lang ")"))
    (.delete temp-mp3)
    (.delete temp-wav)
    temp-ogg))
