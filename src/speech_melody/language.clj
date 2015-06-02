(ns speech-melody.language
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [robert.bruce :refer [try-try-again]]
            [clojure.data.json :as json]))

(defn- supported [lang {:keys [languages]}]
  (when (and lang ((set (map str/lower-case languages)) (str/lower-case lang)))
    lang))

(defn- extract [text config]
  (let [re #"\/([a-zA-Z]{2}(\-[a-zA-Z]{2,4}(\-[a-zA-Z]{2})?)?)\b"]
    (if-let [lang (-> (re-find re text) second (supported config))]
      {:text (-> (str/replace text (str "/" lang) "") str/trim)
       :lang lang})))

(defmulti ^{:private true} detect (fn [_ {:keys [language-detector]}] language-detector))

(defmethod detect :detectlanguage.com
  [text {{:keys [api-key]} :detectlanguage.com, :keys [try-options]}]
  (let [detection #(let [response (-> (str "http://ws.detectlanguage.com/0.2/detect?"
                                           (client/generate-query-string {"q" text, "key" api-key}))
                                      client/post :body json/read-str)]
                    (let [data (get response "data")]
                      (when-not data
                        (throw (ex-info "No data" {:text text, :api-key api-key, :response response})))
                      (when-let [detections (seq (get data "detections"))]
                        (get (first detections) "language"))))]
    (try-try-again try-options detection)))

(defmethod detect :none [& _] nil)

(defn text-lang [text {:keys [default-language default-english] :as config}]
  (or (extract text config)
      {:text text
       :lang (or (-> (detect text config)
                     (supported config)
                     (#(if (= % "en") default-english %)))
                 default-language)}))
